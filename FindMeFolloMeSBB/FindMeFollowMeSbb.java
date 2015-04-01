package com.opencloud.slee.services.sip.fmfm;

import com.opencloud.slee.services.sip.common.OCSipSbb;
import com.opencloud.slee.services.sip.proxy.Proxy;
import com.opencloud.slee.services.sip.proxy.ProxyResponseListener;
import com.opencloud.slee.services.sip.fmfm.profile.FMFMSubscriberProfileCMP;
import com.opencloud.slee.services.sip.fmfm.jdbc.JDBCSubscriberLookup;

import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.ClientTransaction;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.*;
import javax.slee.profile.*;
import javax.slee.facilities.NameAlreadyBoundException;
import javax.slee.facilities.TraceLevel;
import javax.slee.nullactivity.NullActivity;
import javax.naming.InitialContext;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import net.java.slee.resource.sip.CancelRequestEvent;

/**
 * Find Me/Follow Me service.
 *
 * Each SIP transaction is handled by a separate FMFM SBB Entity,
 * call state is in a shared ACI, named by call ID. Attach to the
 * ACI during each SIP transaction.
 */
public abstract class FindMeFollowMeSbb extends OCSipSbb {

    public void setSbbContext(SbbContext context) {
        super.setSbbContext(context);
        try {
            Context myEnv = (Context) new InitialContext().lookup("java:comp/env");
            boolean useJDBC = ((Boolean)myEnv.lookup("fmfmUseJDBC")).booleanValue();
            if (useJDBC) {
                DataSource ds = (DataSource) myEnv.lookup("jdbc/FMFMSubscribers");
                lookup = new JDBCSubscriberLookup(ds, getSbbTracer());
            }
            else { // profiles
                String profileTableName = (String) myEnv.lookup("fmfmProfileTableName");
                lookup = new FMFMProfileLookup(profileTableName);
            }
        } catch (NamingException ne) {
            severe("unable to set SBB context", ne);
        }
    }

    protected String getTraceMessageType() { return "FMFM"; }

    public void onInviteRequest(RequestEvent event, ActivityContextInterface aci) {
        // Is this INVITE part of an existing call (re-INVITE) or initiating
        // a new call?
        Request request = event.getRequest();
        if (isTraceable(TraceLevel.FINEST))
            finest("received INVITE request:\n" + request);
        CallStateACI state = lookupCallState(request);
        if (state == null) {
            // New INVITE request - go to connecting state and await response
            finer("got INVITE for new call");
            setConnectingState(CONNECTING_INITIAL);
        }
        else {
            finer("got re-INVITE on existing call");
        }
        createProxy().proxyRequest((ProxyResponseListener)getSbbLocalObject(), request);
    }

    public void onAckRequest(RequestEvent event, ActivityContextInterface aci) {
        aci.detach(getSbbLocalObject()); // detach from the ACK's server txn
        if (isTraceable(TraceLevel.FINEST))
            finest("received ACK request:\n" + event.getRequest());
        createProxy().proxyRequestStateless((Request)event.getRequest().clone());
    }

    public void onByeRequest(RequestEvent event, ActivityContextInterface aci) {
        Request request = event.getRequest();
        if (isTraceable(TraceLevel.FINEST))
            finest("received BYE request:\n" + request);
        CallStateACI state = lookupCallState(request);
        if (state == null) {
            // BYE request for unknown call. Send error response.
            try {
                finer("got BYE for unknown call");
                Response response = getSipMessageFactory().createResponse(Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST, request);
                commitResponse(response);
            } catch (ParseException e) {
                warn("unable to send 481 response", e);
            }
        }
        else {
            finer("got BYE, terminate call");
            disconnect(state);
            createProxy().proxyRequest((ProxyResponseListener)getSbbLocalObject(), event.getRequest());
        }
    }

    public void onCancelRequest(CancelRequestEvent event, ActivityContextInterface aci) {
        // Ask the RA to process the cancel
        if (getSleeSipProvider().acceptCancel(event, true)) {
            // The cancel matched an INVITE txn
            if (isTraceable(TraceLevel.FINEST)) finest("received CANCEL for call:\n" + event.getRequest());
            getProxy().cancel(); // This will terminate any in-progress txns
        }
        else {
            // The cancel did not match any INVITE txn... proxy it statelessly downstream
            if (isTraceable(TraceLevel.FINEST)) finest("received CANCEL for unknown call:\n" + event.getRequest());
            getProxy().proxyRequestStateless(event.getRequest());
        }
    }

    // Called by Proxy child when a response is ready for forwarding
    public void forwardResponse(Response response) {
        switch (response.getStatusCode()/100) {
            case 1:
                onProvisionalResponse(response);
                break;
            case 2:
                onOKResponse(response);
                break;
            default:
                onErrorResponse(response);
        }
    }

    public void receivedResponse(ClientTransaction ct, Response response) {
        // nothing to do
    }

    private void onProvisionalResponse(Response response) {
        // nothing to do
        finer("forwarding provisional response");
        commitResponse(response);
    }

    private void onOKResponse(Response response) {
        if (getConnectingState() == CONNECTING_INITIAL ||
                getConnectingState() == CONNECTING_FMFM) {
            // got an OK response, so we are now connected
            finer("got OK, call connected, create call state");

            CallStateACI callState = createCallState(response);

            if (isTraceable(TraceLevel.FINE)) {
                fine("connected: orig=" + callState.getOriginatingAddress() +
                        ", term=" + callState.getTerminatingAddress() +
                        ", redirected=" + callState.getRedirectedAddress()) ;
            }
        }
        finer("forwarding OK response");
        commitResponse(response);
    }

    private void onErrorResponse(Response response) {
        if (getConnectingState() == CONNECTING_INITIAL) {
            // Got an error response during first connect, try backup addresses
            if (isTraceable(TraceLevel.FINER))
                finer("got " + response.getStatusCode() + " response, trying backup addresses");

            String term = ((SipURI)((ToHeader)response.getHeader(ToHeader.NAME)).getAddress().getURI()).toString();

            List targets = getBackupAddresses(term);
            if (targets == null || targets.isEmpty()) {
                if (isTraceable(TraceLevel.FINER))
                    finer("no backup addresses for " + term +
                            ", terminating call");
                commitResponse(response);
            }
            else {
                setConnectingState(CONNECTING_FMFM);
                // create new proxy and forward
                Proxy p = createProxy();
                p.setForking(true);
                p.proxyRequest((ProxyResponseListener)getSbbLocalObject(),
                        getServerTransaction().getRequest(),
                        (URI[])targets.toArray(new URI[targets.size()]));
            }
        }
        else if (getConnectingState() == CONNECTING_FMFM) {
            // got an error response to FMFM, clean up the call
            if (isTraceable(TraceLevel.FINER))
                finer("got " + response.getStatusCode() +
                        " response, backup addresses unsuccessful, terminating call");
            commitResponse(response);
        }
        else {
            if (isTraceable(TraceLevel.FINER))
                finer("got " + response.getStatusCode() +
                        " response, forwarding normally");
            commitResponse(response);
        }
    }

    /**
     * Get the call state that this message is associated with.
     * @param message
     * @return the ACI, or null if the message is not associated with any call yet
     */
    private CallStateACI lookupCallState(Message message)  {
        String acName = ((CallIdHeader)message.getHeader(CallIdHeader.NAME)).getCallId();
        ActivityContextInterface aci = getACNamingFacility().lookup(acName);
        if (aci == null) return null;
        if (isTraceable(TraceLevel.FINER))
            finer("lookupCallState: found call state ACI: " + acName);
        aci.attach(getSbbLocalObject());
        return asSbbActivityContextInterface(aci);
    }

    /**
     * Called when a call is connected. Create the call state ACI, bind it into AC Naming,
     * and set initialise call data
     */
    private CallStateACI createCallState(Message message)  {
        assert getConnectingState() == CONNECTING_INITIAL ||
                getConnectingState() == CONNECTING_FMFM : "bug: may only create call state when connecting";

        try {
            String acName = ((CallIdHeader)message.getHeader(CallIdHeader.NAME)).getCallId();
            String orig = ((SipURI)((FromHeader)message.getHeader(FromHeader.NAME)).getAddress().getURI()).toString();
            String term = ((SipURI)((ToHeader)message.getHeader(ToHeader.NAME)).getAddress().getURI()).toString();
            String redirected = ((SipURI)((ContactHeader)message.getHeader(ContactHeader.NAME)).getAddress().getURI()).toString();
            if (redirected == null || redirected.equals(term)) {
                // Actual address is same as original - don't set redirectingAddress
                redirected = null;
            }

            NullActivity n = getNullActivityFactory().createNullActivity();
            ActivityContextInterface aci = getNullACIFactory().getActivityContextInterface(n);

            CallStateACI callState = asSbbActivityContextInterface(aci);
            if (isTraceable(TraceLevel.FINER))
                finer("creating call state ACI: " + acName);

            getACNamingFacility().bind(callState, acName);

            callState.setCallID(acName);
            callState.setOriginatingAddress(orig);
            callState.setTerminatingAddress(term);
            if (redirected != null) callState.setRedirectedAddress(redirected);
            callState.setCallStartTime(System.currentTimeMillis());

            return callState;

        } catch (UnrecognizedActivityException e) {
            throw new RuntimeException(e); // should never see this for null ACs
        } catch (NameAlreadyBoundException n) {
            throw new RuntimeException("bug: name must not be already bound", n);
        }
    }

    private void disconnect(CallStateACI callState) {
        // end the underlying activity, this will remove name binding

        if (isTraceable(TraceLevel.FINE)) {
            fine("disconnected: orig=" + callState.getOriginatingAddress() +
                    ", term=" + callState.getTerminatingAddress() +
                    ", redirected=" + callState.getRedirectedAddress() +
                    ", duration=" + (System.currentTimeMillis() - callState.getCallStartTime()) + "ms");
            if (isTraceable(TraceLevel.FINER))
                finer("disconnect: removing call state ACI: " + callState.getCallID());
        }

        callState.detach(getSbbLocalObject());
        ((NullActivity)callState.getActivity()).endActivity();
    }

    public ServerTransaction getServerTransaction() {
        ActivityContextInterface aci = getServerTransactionACI();
        return aci == null ? null : (ServerTransaction) aci.getActivity();
    }

    public ActivityContextInterface getServerTransactionACI() {
        ActivityContextInterface[] acis = getSbbContext().getActivities();
        for (int i = 0; i < acis.length; i++) {
            if (acis[i].getActivity() instanceof ServerTransaction)
                return acis[i];
        }
        return null;
    }

    private void commitResponse(Response response) {
        ActivityContextInterface aci = getServerTransactionACI();
        ServerTransaction st = (ServerTransaction) aci.getActivity();
        try {
            if (response.getStatusCode() >= 200) {
                finer("final response, detaching all activities");
                detachAllActivities();
            }
            if (isTraceable(TraceLevel.FINEST))
                finest("forwarding response: \n" + response);
            st.sendResponse(response);
        } catch (Exception e) {
            warn("unable to send response", e);
        }
    }

    private Proxy createProxy() {
        try {
            ChildRelation cr = getProxyChildRelation();
            Proxy p = (Proxy) cr.create();
            return p;
        } catch (CreateException e) {
            throw new RuntimeException(e);
        }
    }

    private Proxy getProxy() {
        // get the proxy we are attached to
        ChildRelation cr = getProxyChildRelation();
        return (Proxy) cr.iterator().next();
    }

    // returns a list of SIP URIs
    private List getBackupAddresses(String sipAddress) {
        FMFMSubscriber profile = lookup.lookup(new Address(AddressPlan.SIP, sipAddress));
        if (profile == null) return null;

        Address[] addresses = profile.getBackupAddresses();
        ArrayList uris = new ArrayList(addresses.length);
        for (int i = 0; i < addresses.length; i++) {
            String address = addresses[i].getAddressString();
            try {
                SipURI uri = (SipURI) getSipAddressFactory().createURI(address);
                uris.add(uri);
            } catch (ParseException e) {
                warn("unable to create uri for " + address);
            }
        }
        if (isTraceable(TraceLevel.FINER))
            finer("backup addresses for: " + sipAddress + ": " + uris);
        return uris;
    }

    private class FMFMProfileLookup implements FMFMSubscriberLookup {
        FMFMProfileLookup(String profileTableName) {
            this.profileTableName = profileTableName;
        }

        public FMFMSubscriber lookup(Address address) {
            try {
                ProfileID id = getProfileFacility().getProfileByIndexedAttribute(profileTableName, "addresses", address);
                if (id == null) return null;
                final FMFMSubscriberProfileCMP profile = getFMFMSubscriberProfile(id);
                return new FMFMSubscriber() {
                    public Address[] getAddresses() { return profile.getAddresses(); }
                    public Address[] getBackupAddresses() { return profile.getBackupAddresses(); }
                };
            } catch (Exception e) {
                if (isTraceable(TraceLevel.FINEST))
                    finest("error looking up profile for " + address, e);
                return null;
            }
        }

        private final String profileTableName;
    }

    public abstract ChildRelation getProxyChildRelation();
    public abstract CallStateACI asSbbActivityContextInterface(ActivityContextInterface aci);
    public abstract FMFMSubscriberProfileCMP getFMFMSubscriberProfile(ProfileID id)
            throws UnrecognizedProfileTableNameException, UnrecognizedProfileNameException;

    // Set if the SBB entity is in the process of connecting a call, ie. it has
    // received an INVITE, and is waiting for a response (either to the
    // initial proxied INVITE request, or a backup "follow-me" request.
    public abstract int getConnectingState();
    public abstract void setConnectingState(int state);

    private FMFMSubscriberLookup lookup;

    private static final int CONNECTING_INITIAL = 1;
    private static final int CONNECTING_FMFM = 2;

}
