package com.opencloud.slee.services.sip.b2bua;

import com.opencloud.slee.services.sip.common.OCSipSbb;
import com.opencloud.slee.services.sip.location.LocationService;
import com.opencloud.slee.services.sip.location.Registration;

import com.opencloud.slee.services.sip.location.*;
import net.java.slee.resource.sip.CancelRequestEvent;


import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.DialogForkedEvent;

import com.opencloud.javax.sip.slee.OCSleeSipProvider;

import net.java.slee.resource.sip.CancelRequestEvent;
import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.DialogForkedEvent;


import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sip.*;
import javax.sip.InvalidArgumentException;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.RSeqHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.*;
import javax.slee.facilities.TraceLevel;
import java.text.ParseException;
import java.util.List;

import javax.sql.DataSource;

import java.text.ParseException;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


public abstract class BackToBackUserAgentSbb extends OCSipSbb {
    protected String getTraceMessageType() { return "B2BUA"; }

	DataSource ds;
String Caller_URI;
String Callee_URI;
String inviteeventmsg;
String screenfrom, screenfromname;
String screento,screentoname;

    public void setSbbContext(SbbContext context) {
        super.setSbbContext(context);
        try {
            Context myEnv = (Context) new InitialContext().lookup("java:comp/env");
            domains = parseDomains((String)myEnv.lookup("domains"));
        } catch (NamingException e) {
            severe("Could not set SBB context", e);
        }
    }

    /**
     * An out-of-dialog INVITE has been received - this is the initial event.
     */
    public void onInitialInvite(RequestEvent event, ActivityContextInterface aci) {
   
        OCSleeSipProvider p = (OCSleeSipProvider) event.getSource();

        ServerTransaction st = event.getServerTransaction();

        setInitialServerTransaction(aci);

/*............................................................................................*/
       Callee_URI=st.getRequest().getRequestURI().toString();

       inviteeventmsg = event.getRequest().toString();
        
		 	int indexstart = inviteeventmsg.indexOf("From:",0);
			int start= indexstart;

			int indexend = inviteeventmsg.indexOf(";tag=",0);
			int end = indexend;

			finest(" start " + start +" : end "+ end);
			Caller_URI = inviteeventmsg.substring(start,end);

			finest("Caller_URI.length()"+Caller_URI.length() );

			Caller_URI=Caller_URI.substring(7, Caller_URI.length()-20);

			finest(" caller "+Caller_URI );
					
/*.............................................................................................*/  				
 
        if (isTraceable(TraceLevel.FINEST)) finest("received initial INVITE:\n" + event.getRequest());

        try {

            finer("initializing UAS dialog");
            DialogActivity incomingDialog = (DialogActivity) getSleeSipProvider().getNewDialog(st);
            finer("initializing UAC dialog");
            DialogActivity outgoingDialog = getSleeSipProvider().getNewDialog(incomingDialog, true);

            ActivityContextInterface outgoingDialogACI = getSipACIFactory().getActivityContextInterface(outgoingDialog);
            ActivityContextInterface incomingDialogACI = getSipACIFactory().getActivityContextInterface(incomingDialog);

            incomingDialogACI.attach(getSbbLocalObject());
            outgoingDialogACI.attach(getSbbLocalObject());

            getSbbContext().maskEvent(OUTGOING_EVENT_MASK, outgoingDialogACI);

            setIncomingDialog(incomingDialogACI);
            setOutgoingDialog(outgoingDialogACI);
//--------------------------------------------------------------------------------------------------------------
				    try 
				   { 
							 finest(".................... connecting to datasource ..");
							 Context myEnv = (Context) new InitialContext().lookup("java:resource"); 
							 ds = (DataSource) myEnv.lookup("jdbc/ExternalDataSource");
					 }
					 catch (NamingException ne) 
					{ 

								severe("Could not set SBB context", ne); 
					}   

					try
					{
							finest(".................... firing querry ..");			
							Connection conn = ds.getConnection();  
					 		String sql= "SELECT calleename, calleesipuri ,callername , callersipuri from callscreening where calleesipuri=? ";
							PreparedStatement stam = conn.prepareStatement(sql);
							stam.setString(1,Callee_URI);
							ResultSet rs = stam.executeQuery();

							while (rs.next()) 
							{
										screenfromname=rs.getString(1);
										screenfrom=rs.getString(2);
										screentoname=rs.getString(3);
										screento=rs.getString(4);

										finest(".................... db callee name "+ screenfromname +" uri "+ screenfrom );
										finest(".................... db caller name "+ screentoname +" uri "+ screento);
							}
							rs.close();
							stam.close();
		
			 		}
	
			 		catch (Exception e)
			 		{
			 			severe("................Exception with postgres", e); 	
			 		}		

//--------------------------------------------------------------------------------------------------------------
						if( screenfrom.contains(Callee_URI) && screento.contains(Caller_URI))
						{
										finest(".................... screen match found  ..");

										//option 1 										
										//throw new Exception(" caller screened ");

										//option2 
										//Response response = getSipMessageFactory().createResponse(403, st.getRequest());
                    //st.sendResponse(response);

										forwardRequesttoIVR(st,outgoingDialog,true);
						}
						else
						{
            			forwardRequest(st, outgoingDialog, true);
						}
        } catch (Exception e) {
            warn("failed to forward initial request", e);
            sendErrorResponse(st, Response.SERVER_INTERNAL_ERROR);
        }
    }

    public void onCancel(CancelRequestEvent event, ActivityContextInterface aci) {
        if (isTraceable(TraceLevel.FINEST)) finest("received CANCEL request:\n" + event.getRequest());
        handleCancel(event, aci.equals(getInitialServerTransaction()) || aci.equals(getIncomingDialog())
                ? getOutgoingDialog() : getIncomingDialog());
    }

    public void onDialogForked(DialogForkedEvent event, ActivityContextInterface aci) {
        finest("dialog forked");
        handleFork(event, aci);
    }

    // Responses
//---------------------------------------------------------------------------------------------------------------------------------------------------------
    public void on1xxResponse(ResponseEvent event, ActivityContextInterface aci) {
        Response response = event.getResponse();
        if (response.getHeader(RSeqHeader.NAME) != null) {
            processReliableResponse(event, aci);
        }
        else processResponse(event, aci);
    }

     public void on2xxResponse(ResponseEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............resp : 2xx response ");
        OCSleeSipProvider p = (OCSleeSipProvider) event.getSource();
		    processResponse(event, aci);      
    }

    public void on3xxResponse(ResponseEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............resp : 3xx response ");
        processResponse(event, aci);
    }

    public void on4xxResponse(ResponseEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............resp : 4xx response ");
        processResponse(event, aci);
    }

    public void on5xxResponse(ResponseEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............resp : 5xx response ");
        processResponse(event, aci);
    }

    public void on6xxResponse(ResponseEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............resp : 6xx response ");
        processResponse(event, aci);
    }

    //---------------------------------------------------------------------- Mid-dialog requests-------------------------------------------------------------

    public void onAck(RequestEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............req  : ack  ");
        processMidDialogRequest(event, aci);
    }

    public void onBye(RequestEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))  finest(".............req  : bye  ");
        OCSleeSipProvider p = (OCSleeSipProvider) event.getSource();
        processMidDialogRequest(event, aci);
    }

    public void onReInvite(RequestEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............req  : reinvite   ");
        processMidDialogRequest(event, aci);
    }

    public void onPrack(RequestEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............req  : prack  ");
        processMidDialogRequest(event, aci);
    }

    public void onUpdate(RequestEvent event, ActivityContextInterface aci) {
																																														  		if (isTraceable(TraceLevel.FINEST))  finest(".............req  : update  ");
        processMidDialogRequest(event, aci);
    }

    public void onInfo(RequestEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............req  : info  ");
        processMidDialogRequest(event, aci);
    }

    public void onSubscribe(RequestEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............req  : subscribe  ");
        processMidDialogRequest(event, aci);
    }

    public void onNotify(RequestEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............req  : notify  ");
        processMidDialogRequest(event, aci);
    }

    public void onPublish(RequestEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............req  : publish  ");
        processMidDialogRequest(event, aci);
    }

    public void onRefer(RequestEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............req  : refer  ");
        processMidDialogRequest(event, aci);
    }

    public void onMessage(RequestEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST))	finest(".............req  : msg  ");
        processMidDialogRequest(event, aci);
    }

    public void onUnknownRequest(RequestEvent event, ActivityContextInterface aci) {
																																																if (isTraceable(TraceLevel.FINEST)) finest(".............req  : unknown  ");
        processMidDialogRequest(event, aci);
    }


    public void onTransactionTimeout(TimeoutEvent event, ActivityContextInterface aci) {
        if (event.isServerTransaction()) return;
        ClientTransaction ct = event.getClientTransaction();
        if (isTraceable(TraceLevel.FINER)) finer("transaction " + ct + " timed out");
        if (getCancelled()) {
            // is this an error response for a cancelled INVITE? If so, consume the response here, RA has
            // already responded to upstream host.
            if (Request.INVITE.equals(ct.getRequest().getMethod())) {
                if (isTraceable(TraceLevel.FINER)) finer("no need to forward timeout from cancelled INVITE");
                setCancelled(false); // reset
            }
        }
    }


    private void handleFork(DialogForkedEvent event, ActivityContextInterface aci) {
        // Don't need to stay attached to the original dialog
        aci.detach(getSbbLocalObject());
        ResponseEvent responseEvent = event.getResponseEvent();
        ServerTransaction st;
        DialogActivity uac;
        if (isTraceable(TraceLevel.FINEST)) finest("received forked response on dialog " + aci.getActivity() + ":\n" + responseEvent.getResponse());
        try {
            uac = event.getNewDialog();
            ActivityContextInterface uacACI = getSipACIFactory().getActivityContextInterface(uac);
            uacACI.attach(getSbbLocalObject());
            setOutgoingDialog(uacACI);
            st = uac.getAssociatedServerTransaction(responseEvent.getClientTransaction());
        } catch (Exception e) {
            warn("unable to continue with forked UAC dialog", e);
            return;
        }

        try {
            DialogActivity uas = getSleeSipProvider().forwardForkedResponse(st, responseEvent.getResponse());
            ActivityContextInterface uasACI = getSipACIFactory().getActivityContextInterface(uas);
            uasACI.attach(getSbbLocalObject());
            setIncomingDialog(uasACI);
        } catch (Exception e) {
            warn("unable to continue with forked UAS dialog", e);
            uac.delete();
        }
    }

    /**
     * Send a CANCEL on the given dialog
     */
    private void handleCancel(CancelRequestEvent cancelEvent, ActivityContextInterface dialogACI) {
        // Get the RA to respond to the CANCEL
        if (getSleeSipProvider().acceptCancel(cancelEvent, false)) {
            // Cancel matched our INVITE - forward the CANCEL
            DialogActivity dialog = (DialogActivity) dialogACI.getActivity();
            if (isTraceable(TraceLevel.FINEST)) finest("sending CANCEL on dialog " + dialog);
            try {
                dialog.sendCancel();
                setCancelled(true); // This stops us from forwarding the CANCEL response
            } catch (SipException e) {
                warn("failed to send CANCEL", e);
            }
        }
        // else CANCEL did not match. RA has sent 481 response, nothing to do.
    }

    //---------------------------------------------------------- Helpers---------------------------------------------------------------------------
    /**
     * A request was received on one of our dialogs. Forward it to the other dialog.
     */
    private void processMidDialogRequest(RequestEvent event, ActivityContextInterface dialogACI) {
        if (isTraceable(TraceLevel.FINEST)) finest("received mid-dialog request on dialog " + dialogACI.getActivity() + ":\n" + event.getRequest());
        try {
            // Find the dialog to forward the request on
            ActivityContextInterface peerACI = getPeerDialog(dialogACI);
            forwardRequest(event.getServerTransaction(), (DialogActivity)peerACI.getActivity(), false);
        } catch (SipException e) {
            warn("failed to forward request", e);
            sendErrorResponse(event.getServerTransaction(), Response.SERVER_INTERNAL_ERROR);
        }
    }

    /**
     * A response was received on one of our dialogs. Forward it to the other dialog.
     */
    private void processResponse(ResponseEvent event, ActivityContextInterface aci) {
        Response response = event.getResponse();
        if (isTraceable(TraceLevel.FINEST)) finest("received response on dialog " + aci.getActivity() + ":\n" + event.getResponse());
        if (getCancelled()) {
            // is this an error response for a cancelled INVITE? If so, consume the response here, RA has
            // already responded to upstream host, and the peer dialog will have ended.
            if (response.getStatusCode() >= 400 &&
                    Request.INVITE.equals(((CSeqHeader)response.getHeader(CSeqHeader.NAME)).getMethod())) {
                if (isTraceable(TraceLevel.FINER)) finer("no need to forward response from cancelled INVITE");
                setCancelled(false); // reset
                return;
            }
        }

        try {
            // Find the dialog to forward the response on
            ActivityContextInterface peerACI = getPeerDialog(aci);
            forwardResponse((DialogActivity)aci.getActivity(), (DialogActivity)peerACI.getActivity(), event.getClientTransaction(), response);
        } catch (SipException e) {
            warn("failed to forward response", e);
        }
    }

    private void processReliableResponse(ResponseEvent event, ActivityContextInterface aci) {
        Response response = event.getResponse();
        if (isTraceable(TraceLevel.FINEST)) finest("received reliable response on dialog " + aci.getActivity() + ":\n" + event.getResponse());

        try {
            // Find the dialog to forward the response on
            ActivityContextInterface peerACI = getPeerDialog(aci);
            forwardReliableResponse((DialogActivity)aci.getActivity(), (DialogActivity)peerACI.getActivity(), event.getClientTransaction(), response);
        } catch (SipException e) {
            warn("failed to forward response", e);
        }
    }

    private ActivityContextInterface getPeerDialog(ActivityContextInterface aci) throws SipException {
        if (aci.equals(getIncomingDialog())) return getOutgoingDialog();
        if (aci.equals(getOutgoingDialog())) return getIncomingDialog();
        throw new SipException("could not find peer dialog");
    }

    private void forwardRequest(ServerTransaction st, DialogActivity out, boolean initial) throws SipException {
        // Copies the request, setting the appropriate headers for the dialog.
        Request incomingRequest = st.getRequest();
        Request outgoingRequest = out.createRequest(incomingRequest);

        if (initial) {
            // On initial request only, check if the destination address is inside one of our domains
            URI requestURI = incomingRequest.getRequestURI();
            if (isLocalDomain(requestURI, domains)) {
                if (isTraceable(TraceLevel.FINE)) fine(requestURI + " is in a local domain, lookup address in location service");
                URI registeredAddress = lookupRegisteredAddress(requestURI);

                if (registeredAddress == null) {
                    if (isTraceable(TraceLevel.FINE)) fine("no registered address found for " + requestURI);
                    sendErrorResponse(st, Response.TEMPORARILY_UNAVAILABLE);
                    return;
                }

                if (isTraceable(TraceLevel.FINE)) fine("found registered address: " + registeredAddress);
                outgoingRequest.setRequestURI(registeredAddress);
            }
            else {
                if (isTraceable(TraceLevel.FINE)) fine(requestURI + " is outside our domain, forwarding");
            }
        }

        if (isTraceable(TraceLevel.FINEST)) finest("forwarding request on dialog " + out + ":\n" + outgoingRequest);
        if (incomingRequest.getMethod().equals(Request.ACK)) {

            out.sendAck(outgoingRequest);
        }
        else {
            ClientTransaction ct = out.sendRequest(outgoingRequest);
            out.associateServerTransaction(ct, st);
        }
    }

private void forwardRequesttoIVR(ServerTransaction st, DialogActivity out, boolean initial) throws SipException {
        // Copies the request, setting the appropriate headers for the dialog.
        Request incomingRequest = st.getRequest();
        Request outgoingRequest = out.createRequest(incomingRequest);

        try
				{
		      URI callscreening_voice = getSipAddressFactory().createURI("sip:11@10.1.5.15:5070");
		      
		      if (isTraceable(TraceLevel.FINEST)) finest(" IVR : forwarding request on dialog " + out + ":\n" + outgoingRequest);
					outgoingRequest.setRequestURI(callscreening_voice);
				
				 }
				catch(Exception e)
				{
					finest("IVR : exception in sip factory in forward requesrt");
				}

        if (incomingRequest.getMethod().equals(Request.ACK)) 
				{
            out.sendAck(outgoingRequest);
        }
        else
				{
            ClientTransaction ct = out.sendRequest(outgoingRequest);
            out.associateServerTransaction(ct, st);
        }
    }

    @SuppressWarnings("unchecked")
    private URI lookupRegisteredAddress(URI publicAddress) {
        Registration reg = null;
        try {
            LocationService ls = (LocationService) getLocationServiceChildRelation().create();
            reg = ls.getRegistration(getCanonicalAddress(publicAddress));
        } catch (CreateException e) {
            warn("could not create location service child SBB", e); // log and carry on as if registration not found
        }

        if (reg == null) return null;
        List<Registration.Contact> contacts = (List<Registration.Contact>) reg.getContacts();
        float maxQ = -1f; // q-values are from 0.0 - 1.0
        String best = null;
        for (Registration.Contact contact : contacts) {
            if (contact.getQValue() > maxQ) {
                best = contact.getContactURI();
                maxQ = contact.getQValue();
            }
        }
        if (best == null) return null;
        try {
            return getSleeSipProvider().getAddressFactory().createURI(best);
        } catch (ParseException e) {
            warn("failed to parse URI: " + best, e);
            return null;
        }
    }

    private void forwardResponse(DialogActivity in, DialogActivity out, ClientTransaction ct, Response receivedResponse) throws SipException {
        // Find the original server transaction that this response should be forwarded on.
        ServerTransaction st = in.getAssociatedServerTransaction(ct); // could be null
        if (st == null) throw new SipException("could not find associated server transaction");
        // Copy the response across, setting the appropriate headers for the dialog
        Response outgoingResponse = out.createResponse(st, receivedResponse);
        if (isTraceable(TraceLevel.FINEST)) finest("forwarding response on dialog " + out + ":\n" + outgoingResponse);
        // Forward response upstream.
        try {
            st.sendResponse(outgoingResponse);
        } catch (InvalidArgumentException e) {
            throw new SipException("invalid response", e);
        }
    }

    private void forwardReliableResponse(DialogActivity in, DialogActivity out, ClientTransaction ct, Response receivedResponse) throws SipException {
        // Find the original server transaction that this response should be forwarded on.
        ServerTransaction st = in.getAssociatedServerTransaction(ct); // could be null
        if (st == null) throw new SipException("could not find associated server transaction");
        // Copy the response across, setting the appropriate headers for the dialog
        Response outgoingResponse = out.createResponse(st, receivedResponse);
        if (isTraceable(TraceLevel.FINEST)) finest("forwarding reliable response on dialog " + out + ":\n" + outgoingResponse);
        // Forward response upstream.
        out.sendReliableProvisionalResponse(outgoingResponse);
    }

    private void sendErrorResponse(ServerTransaction st, int statusCode) {
        try {
            Response response = getSipMessageFactory().createResponse(statusCode, st.getRequest());
            st.sendResponse(response);
        } catch (Exception e) {
            warn("failed to send error response", e);
        }
    }

    public abstract void setInitialServerTransaction(ActivityContextInterface aci);
    public abstract ActivityContextInterface getInitialServerTransaction();

    public abstract void setIncomingDialog(ActivityContextInterface aci);
    public abstract ActivityContextInterface getIncomingDialog();

    public abstract void setOutgoingDialog(ActivityContextInterface aci);
    public abstract ActivityContextInterface getOutgoingDialog();

    // Set this flag if B2BUA has just sent a CANCEL - it doesn't have to forward the response
    // of the cancelled INVITE client transaction.
    public abstract void setCancelled(boolean cancelled);
    public abstract boolean getCancelled();

    public abstract ChildRelation getLocationServiceChildRelation();

    private String[] domains;

    private static final String[] OUTGOING_EVENT_MASK = new String[] { "DialogForked" };
}
