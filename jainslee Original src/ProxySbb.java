package com.opencloud.slee.services.sip.proxy;

import com.opencloud.slee.services.sip.common.*;
import com.opencloud.slee.services.sip.location.LocationService;
import com.opencloud.javax.sip.slee.OCSleeSipProvider;
import com.opencloud.javax.sip.header.OCHeaderFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sip.*;
import javax.sip.address.URI;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.ViaHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.sip.message.MessageFactory;
import javax.slee.*;
import javax.slee.facilities.*;
import javax.slee.nullactivity.NullActivity;
import java.text.ParseException;
import java.util.Iterator;

import net.java.slee.resource.sip.CancelRequestEvent;

/**
 * Implements generic SIP proxy behaviour. May be used standalone or
 * as a component (via the {@link Proxy} SBB local interface) by other
 * SIP SBBs that need to proxy requests.
 */
public abstract class ProxySbb extends OCSipSbb {
    public void setSbbContext(SbbContext context) {
        super.setSbbContext(context);
        try {
            Context myEnv = (Context) new InitialContext().lookup("java:comp/env");
            // get proxy config params
            timerC = ((Integer)myEnv.lookup("timerC")).intValue();
            router = createProxyRouter(myEnv);
        } catch (NamingException e) {
            severe("unable to set SBB context", e);
        }
    }

    public void sbbCreate() throws CreateException {
        super.sbbCreate();
        setRecordRoute(true); // Add Record-Route header by default
        setUseLocationService(true); // Use the location service by default
    }

    public InitialEventSelector initialEventSelect(InitialEventSelector ies) {
        // Additional check for initial events that are stateless responses.
        // We are only interested in stray 2xx responses to INVITE, RFC3261 says a proxy
        // MUST forward these to maintain end-to-end robustness of INVITE transactions.
        Object event = ies.getEvent();
        if (event instanceof ResponseEvent) {
            Response response = ((ResponseEvent)event).getResponse();
            int statusCode = response.getStatusCode();
            String method = ((CSeqHeader)response.getHeader(CSeqHeader.NAME)).getMethod();
            if (statusCode >= 200 && statusCode <= 299 &&
                    method.equals(Request.INVITE) &&
                    !(ies.getActivity() instanceof ClientTransaction)) {
                // This is a stray 2xx response to INVITE, with no client transaction.
                // Make it an initial event so the proxy will forward it.
                ies.setInitialEvent(true);
            }
            else {
                ies.setInitialEvent(false);
            }
        }
        return ies;
    }

    protected ProxyRouter createProxyRouter(Context myEnv) throws NamingException {
        final String[] domains = parseDomains((String) myEnv.lookup("domains"));
        final boolean loopDetection = (Boolean) myEnv.lookup("loopDetection");

        ProxyConfig config = new ProxyConfig() {
            public OCSleeSipProvider getSipProvider() { return ProxySbb.this.getSleeSipProvider(); }
            public AddressFactory getSipAddressFactory() { return ProxySbb.this.getSipAddressFactory(); }
            public OCHeaderFactory getSipHeaderFactory() { return ProxySbb.this.getOCHeaderFactory(); }
            public MessageFactory getSipMessageFactory() { return ProxySbb.this.getSipMessageFactory(); }
            public String[] getProxyDomains() { return domains; }
            public boolean isRecordRouteEnabled() { return getRecordRoute(); }
            public boolean isForkingEnabled() { return getForking(); }
            public boolean isLoopDetectionEnabled() { return loopDetection; }
            public LocationService getLocationService() {
                try {
                    return getUseLocationService() ? ProxySbb.this.getLocationService() : null;
                } catch (CreateException e) {
                    warn("unable to create location service SBB");
                    return null;
                }
            }
            public boolean useLocationService() { return getUseLocationService(); }
        };
        return new ProxyRouter(config, getSbbTracer());
    }

    protected String getTraceMessageType() { return "ProxySbb"; }

    // Event handlers - SIP requests

    public void onInviteRequest(RequestEvent event, ActivityContextInterface aci) {
        processIncomingRequest(aci, event.getRequest());
    }
    public void onAckRequest(RequestEvent event, ActivityContextInterface aci) {
        aci.detach(getSbbLocalObject()); // detach from the ACK's server txn
        proxyRequestStateless((Request)event.getRequest().clone());
    }
    public void onByeRequest(RequestEvent event, ActivityContextInterface aci) {
        processIncomingRequest(aci, event.getRequest());
    }

    public void onCancelRequest(CancelRequestEvent event, ActivityContextInterface aci) {
        // CANCEL handling:
        // There are 2 possibilities for a proxy (not using dialogs)
        // 1. If the CANCEL matches an active INVITE server transaction, the RA fires
        //    the CANCEL event on the INVITE ServerTransaction activity.
        // 2. If there is no matching INVITE, the RA will fire the CANCEL on it's
        //    own ServerTransaction activity.
        //
        // If (1), the proxy must respond to the CANCEL with 200 OK, and cancel
        // any remaining branches.
        //
        // If (2), the proxy must statelessly forward the CANCEL downstream.

        // We can use the RA's convenience method to automatically generate the CANCEL
        // response, if there was a match. If there was no match, we must forward the
        // CANCEL statelessly.
        if (getSleeSipProvider().acceptCancel(event, true)) {
            // (1) Matched an INVITE - cancel our branches
            if (cancel()) return;
        }
        // (2) no matching invite, or no response context
        // Forward CANCEL downstream statelessly
        proxyRequestStateless((Request)event.getRequest().clone());
    }

    public void onOptionsRequest(RequestEvent event, ActivityContextInterface aci) {
        Request request = event.getRequest();
        if (request.getRequestURI().isSipURI() && router.isProxySipURI((SipURI)request.getRequestURI())) {
            aci.detach(getSbbLocalObject());
            handleOptions(event);
        }
        else processIncomingRequest(aci, event.getRequest());
    }
    public void onRegisterRequest(RequestEvent event, ActivityContextInterface aci) {
        processIncomingRequest(aci, event.getRequest());
    }
    public void onInfoRequest(RequestEvent event, ActivityContextInterface aci) {
        processIncomingRequest(aci, event.getRequest());
    }
    public void onUpdateRequest(RequestEvent event, ActivityContextInterface aci) {
        processIncomingRequest(aci, event.getRequest());
    }
    public void onPrackRequest(RequestEvent event, ActivityContextInterface aci) {
        processIncomingRequest(aci, event.getRequest());
    }
    public void onSubscribeRequest(RequestEvent event, ActivityContextInterface aci) {
        processIncomingRequest(aci, event.getRequest());
    }
    public void onNotifyRequest(RequestEvent event, ActivityContextInterface aci) {
        processIncomingRequest(aci, event.getRequest());
    }
    public void onReferRequest(RequestEvent event, ActivityContextInterface aci) {
        processIncomingRequest(aci, event.getRequest());
    }
    public void onMessageRequest(RequestEvent event, ActivityContextInterface aci) {
        processIncomingRequest(aci, event.getRequest());
    }
    public void onExtensionRequest(RequestEvent event, ActivityContextInterface aci) {
        processIncomingRequest(aci, event.getRequest());
    }

    // Event handlers - SIP Responses

    public void on100Response(ResponseEvent event, ProxyResponseContextACI aci) {
        if (event.getResponse().getStatusCode() == Response.TRYING) return;
        processIncomingResponse(aci, event.getResponse(), event.getClientTransaction());
    }
    public void on200Response(ResponseEvent event, ProxyResponseContextACI aci) {
        aci.detach(getSbbLocalObject());
        processIncomingResponse(aci, event.getResponse(), event.getClientTransaction());
    }
    public void on300Response(ResponseEvent event, ProxyResponseContextACI aci) {
        aci.detach(getSbbLocalObject());
        processIncomingResponse(aci, event.getResponse(), event.getClientTransaction());
    }
    public void on400Response(ResponseEvent event, ProxyResponseContextACI aci) {
        aci.detach(getSbbLocalObject());
        processIncomingResponse(aci, event.getResponse(), event.getClientTransaction());
    }
    public void on500Response(ResponseEvent event, ProxyResponseContextACI aci) {
        aci.detach(getSbbLocalObject());
        processIncomingResponse(aci, event.getResponse(), event.getClientTransaction());
    }
    public void on600Response(ResponseEvent event, ProxyResponseContextACI aci) {
        aci.detach(getSbbLocalObject());
        processIncomingResponse(aci, event.getResponse(), event.getClientTransaction());
    }

    public void onTransactionTimeout(TimeoutEvent event, ProxyResponseContextACI aci) {
        aci.detach(getSbbLocalObject());
        if (event.isServerTransaction()) return;

        if (Request.CANCEL.equals(event.getClientTransaction().getRequest().getMethod())) {
            finest("onTransactionTimeout: ignoring CANCEL timeout, no need to forward");
            return;
        }
        finer("client txn timed out, adding \"408 Request Timeout\" to response context");
        addErrorToResponseContext(Response.REQUEST_TIMEOUT, null);

        processResponseContexts();
    }

    public void onTimerC(TimerEvent event, ProxyResponseContextACI aci) {
        if (getProxyState() == PROXY_SENT_FINAL_RESPONSE) {
            finer("onTimerC: Timer C fired, final response already sent, nothing to do");
        }
        else if (aci.getResponseStatusCode() == 0) {
            finer("onTimerC: Timer C fired, no provisional responses received, transaction timed out");
            addErrorToResponseContext(Response.REQUEST_TIMEOUT, null);
            processResponseContexts();
        }
        else {
            finer("onTimerC: Timer C fired, no final response received yet, cancelling transaction");
            cancelPendingTransaction(aci);
        }
    }

    // Proxy SBB Local Interface impl

    public void proxyRequest(ProxyResponseListener listener, final Request request) {
        setProxyResponseListener(listener);
        doProxyRequest(request, null);
    }

    public void proxyRequest(ProxyResponseListener listener, final Request request, URI[] targets) {
        setProxyResponseListener(listener);
        doProxyRequest(request, targets);
    }

    public void proxyRequestStateless(final Request request) {
        // 200 OK has already been sent, so if any exceptions, do nothing
        setProxyState(PROXY_SENT_FINAL_RESPONSE); // nothing else to do
        try {
            Request[] requestsToSend = requestProcessing(request, null);
            for (int i = 0; i < requestsToSend.length; i++) {
                if (isTraceable(TraceLevel.FINE)) fine("forwarding: " + request.getMethod() + " to target: " + requestsToSend[i].getRequestURI());
                if (isTraceable(TraceLevel.FINEST)) finest("forwarding request statelessly:\n" + requestsToSend[i]);
                try {
                    getSipProvider().sendRequest(requestsToSend[i]);
                } catch (SipException e) {
                    warn("unable to forward request statelessly", e);
                }
            }
        } catch (Throwable t) {
            warn("unable to forward request statelessly", t);
        }
    }

    public Request requestProcessing(final Request request) throws SipSendErrorResponseException {
        return router.routeRequest(request);
    }

    public Request[] requestProcessing(final Request request, URI[] targets) throws SipSendErrorResponseException {
        return router.routeRequest(request, targets);
    }

    /**
     * Cancel any in-progress client transactions (no final response received yet).
     * If any INVITE client transactions are active, the SBB will send a CANCEL
     * request for each. The client transactions should eventually receive a "487
     * Request Terminated" response from the called party. For non-INVITE client
     * transactions, just detach since the proxy doesn't need those responses anymore.
     * @return true if any matching INVITE client transactions were found and cancelled.
     */
    public boolean cancel() {
        int cancelled = 0;
        ActivityContextInterface[] acis = getSbbContext().getActivities();
        for (int i = 0; i < acis.length; i++) {
            // for each pending invite client txn, cancel it if no final response
            // received. for non-invite txns, just detach so we won't see any
            // responses on them
            if (acis[i].getActivity() instanceof ClientTransaction) {
                ProxyResponseContextACI aci = asSbbActivityContextInterface(acis[i]);

                if (getInvite()) {
                    // The cancelled INVITE transactions will eventually receive a 487
                    // response (or just time out). This will be handled in the normal
                    // response context processing path.
                    cancelPendingTransaction(aci);
                    cancelled++;
                    // If we have already sent the final response, then detach since
                    // we don't need to see the 487 responses
                    if (getProxyState() == PROXY_SENT_FINAL_RESPONSE) {
                        aci.detach(getSbbLocalObject());
                    }
                }
                else {
                    // For non-invite txns just detach, we don't care about their responses now.
                    aci.detach(getSbbLocalObject());
                }
            }
        }
        return cancelled > 0;
    }

    // ProxyResponseListener impl

    public void forwardResponse(Response response) {
        ActivityContextInterface aci = getServerTransactionACI();
        ServerTransaction st = null;
        if (aci != null) {
            if (response.getStatusCode() >= 200) {
                // Final response, won't be using the server transaction any more.
                aci.detach(getSbbLocalObject());
            }
            st = (ServerTransaction) aci.getActivity();
        }

        try {
            if (st != null) {
                if (isTraceable(TraceLevel.FINEST))
                    finest("forwarding response: \n" + response);
                st.sendResponse(response);
            }
            else {
                if (isTraceable(TraceLevel.FINEST))
                    finest("forwarding response statelessly: \n" + response);
                getSipProvider().sendResponse(response);
            }
        } catch (Exception e) {
            warn("unable to send response", e);
        }
    }

    public ServerTransaction getServerTransaction() {
        ActivityContextInterface aci = getServerTransactionACI();
        return aci == null ? null : (ServerTransaction) aci.getActivity();
    }

    public void receivedResponse(ClientTransaction ct, Response response) {
        // default impl, do nothing
    }
    
    protected void doProxyRequest(final Request request, URI[] targets) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        if (getProxyState() != PROXY_INIT) throw new IllegalStateException("already proxying request");

        try {
            Request[] requestsToSend = requestProcessing(request, targets);
            setProxyState(PROXY_WAIT_FOR_FINAL_RESPONSE);
            setInvite(request.getMethod().equals(Request.INVITE));

            for (int i = 0; i < requestsToSend.length; i++) {
                if (isTraceable(TraceLevel.FINE)) fine("forwarding: " + request.getMethod() + " to target: " + requestsToSend[i].getRequestURI());
                // 16.6 Request Forwarding (continued)
                // 10. Forward Request
                ProxyResponseContextACI aci = forwardRequest(requestsToSend[i]);
                // 11. Set timer C
                // If there is already a status code set in the ACI, this is an
                // artificial error response so we don't need to set the timer
                if (getInvite() && aci.getResponseStatusCode() == 0) setTimerC(aci);
            }
            // Check response contexts, in case we are done already
            // (eg. all sends failed).
            processResponseContexts();

        } catch (SipSendErrorResponseException e) {
            // If we cannot continue for some reason, jump here and
            // send an appropriate response
            if (isTraceable(TraceLevel.FINE))
                fine("unable to proxy request (" + e + "), sending " + e.getStatusCode() + " response");
            try {
                Response err = getSipMessageFactory().createResponse(e.getStatusCode(), request);
                sendResponse(err);
            } catch (Exception e1) {
                warn("unable to send error response", e1);
            }
            detachAllActivities();
        } catch (Throwable t) {
            warn("unexpected error proxying request", t);
            try {
                Response err = getSipMessageFactory().createResponse(Response.SERVER_INTERNAL_ERROR, request);
                sendResponse(err);
            } catch (Exception e1) {
                warn("unable to send error response", e1);
            }
            detachAllActivities();
        }
    }

    protected void processIncomingRequest(ActivityContextInterface serverTxnACI, Request request) {
        if (isTraceable(TraceLevel.FINEST))
            finest("processIncomingRequest: incoming request:\n" + request);
        // We have received a request as an initial event. Perform default proxy processing.
        ProxyResponseListener defaultListener = (ProxyResponseListener) getSbbLocalObject();
        serverTxnACI.attach(defaultListener);
        proxyRequest(defaultListener, request);
    }

    /**
     * A real response has been received on a client transaction, perform required
     * proxy response processing.
     */
    protected void processIncomingResponse(ProxyResponseContextACI responseContextACI, 
                                           final Response response,
                                           final ClientTransaction ct) {
        // Check if the response should be forwarded statelessly - only
        // need to do this for 2xx responses to INVITE
        final boolean stateless = ct == null;

        if (isTraceable(TraceLevel.FINEST))
            finest("processIncomingResponse: incoming " + (stateless ? "stateless" : "") + " response:\n" + response);

        // Don't forward responses to CANCEL
        String method = ((CSeqHeader)response.getHeader(CSeqHeader.NAME)).getMethod();
        if (Request.CANCEL.equals(method)) {
            finest("processIncomingResponse: ignoring CANCEL response, no need to forward");
            return;
        }

        if (stateless) {
            // Check if we need to forward the response, otherwise drop it.
            // Initial event selector should ensure we only see 2xx INVITE responses.
            int statusCode = response.getStatusCode();
            if (statusCode >= 200 && statusCode <= 299 && method.equals(Request.INVITE)) {
                setInvite(true);
                setProxyState(PROXY_SENT_FINAL_RESPONSE);
            }
            else {
                if (isTraceable(TraceLevel.FINEST)) finest("not a 2xx response to INVITE, dropping");
                return;
            }
            // Continue response processing - response will be forwarded normally.
        }

        Response newResponse = (Response) response.clone();

        // From RFC3261:
        // 16.7 Response Processing

        // 1. Find appropriate response context
        // Done (implicitly) - Not quite the same thing as in the RFC,
        // here the "response context" is the SBB entity and its attached response context ACIs.

        // 2. Update timer C for provisional responses
        if (getInvite() && !stateless) updateTimerC(responseContextACI, newResponse);

        // Pass up to our listener (before modifying response)
        // so that it can create dialogs etc if necessary
        ProxyResponseListener listener = getProxyResponseListener();
        // Listener may be null if this is a stateless response, just call proxy directly.
        if (listener != null) listener.receivedResponse(ct, newResponse);
        else receivedResponse(ct, newResponse);

        // 3. Remove topmost via
        Iterator it = newResponse.getHeaders(ViaHeader.NAME);
        // Stack will ensure that there is at least one Via header
        it.next();
        it.remove();
        if (!it.hasNext()) {
            // Response was meant for this proxy - should not see any of these
            // but try to do the right thing just in case
            warn("processIncomingResponse: received response directed to this proxy: " + response);
            return;
        }

        // 4. Add the response to the response context
        updateResponseContext(responseContextACI, newResponse);
        // Steps 5-10 continued below...
    }

    /**
     * A response was received, update the response context, send response and/or
     * cancel transactions as required
     * @param response
     */
    protected void updateResponseContext(ProxyResponseContextACI responseContextACI, Response response) {
        int statusCode = response.getStatusCode();
        int shortStatusCode = statusCode/100;

        // 5. Check to see if this response should be forwarded immediately
        // 6. When necessary, choose the best final response from the response context
        // 7. Aggregate authorization header fields if necessary
        // 8. Optionally rewrite Record-Route header field values
        // 9. Forward the response
        // 10. Generate any necessary CANCEL requests

        switch (getProxyState()) {
            case PROXY_WAIT_FOR_FINAL_RESPONSE:

                switch (shortStatusCode) {
                    case 1:
                        // provisional response, forward immediately
                        if (isTraceable(TraceLevel.FINER))
                            finer("received " + statusCode + " response, forward immediately");
                        sendResponse(response);
                        // Still attached to client txn, store current provisional response status code,
                        // will be seen by processResponseContexts so it knows we are still waiting for
                        // a final response.
                        responseContextACI.setResponseStatusCode(statusCode);
                        break;

                    case 2:
                        // OK response, forward immediately and cancel others
                        if (isTraceable(TraceLevel.FINER))
                            finer("received " + statusCode + " response, forward immediately, cancel pending txns");
                        sendResponse(response);
                        cancel();
                        break;

                    case 6:
                        // Global failure, cancel all pending INVITEs
                        if (isTraceable(TraceLevel.FINER))
                            finer("received " + statusCode + " response, add to response context, cancel pending txns");
                        cancel();
                        // We have detached from the client txn, so use a null ACI to store response info
                        addErrorToResponseContext(response);
                        break;

                    default:
                        if (isTraceable(TraceLevel.FINER))
                            finer("received " + statusCode + " response, add to response context");
                        // We have detached from the client txn, so use a null ACI to store response info
                        addErrorToResponseContext(response);
                }
                processResponseContexts();
                break;

            case PROXY_SENT_FINAL_RESPONSE:
                if (shortStatusCode == 2 && getInvite()) {
                    // OK response to INVITE, forward immediately
                    if (isTraceable(TraceLevel.FINER))
                        finer("received late " + statusCode + " response to INVITE, forward immediately");
                    sendResponse(response);
                }
                break;
        }
    }

    protected void cancelPendingTransaction(ProxyResponseContextACI aci) {
        ClientTransaction ct = (ClientTransaction) aci.getActivity();
        // Create and send a CANCEL request
        try {
            if (isTraceable(TraceLevel.FINER))
                finer("cancelling transaction: " + ct);

            Request cancelRequest = ct.createCancel();

            if (isTraceable(TraceLevel.FINEST))
                finest("sending CANCEL request:\n" + cancelRequest);

            ClientTransaction cancelTransaction = getSipProvider().getNewClientTransaction(cancelRequest);
            cancelTransaction.sendRequest();
            // Don't attach - we don't care about the response to the CANCEL.
            // We should receive 487s on the INVITE client txns from the upstream servers.
        } catch (SipException e) {
            warn("unable to cancel INVITE client transaction", e);
        }
    }

    /**
     * Call this after receiving any response or timeout.
     * Select a response to forward based on rules in RFC3261 ch16.7. If we are done
     * then clean up.
     * The attached set will consist of in-progress client transactions, or null ACIs
     * representing error responses.
     */
    protected void processResponseContexts() {
        if (getProxyState() == PROXY_INIT) throw new IllegalStateException();

        ActivityContextInterface[] acis = getSbbContext().getActivities();
        if (acis.length == 0) return; // nothing to do, no "response context"

        if (getProxyState() == PROXY_SENT_FINAL_RESPONSE) {
            // Just detach any remaining response contexts - these would be error
            // responses that arrived on different branches before the 2xx response.
            finer("processResponseContexts: final response sent, cleaning up");
            for (int i = 0; i < acis.length; i++) {
                Object activity = acis[i].getActivity();
                if (activity instanceof NullActivity) {
                    ProxyResponseContextACI proxyACI = asSbbActivityContextInterface(acis[i]);
                    int statusCode = proxyACI.getResponseStatusCode();

                    // The proxy only creates null ACIs with response status code set - therefore if
                    // response status code is still zero then this isn't our null activity, so ignore it.
                    if (statusCode == 0) continue;

                    // Detach from the null activity so it will end
                    acis[i].detach(getSbbLocalObject());
                }
            }
            return;
        }
        else {
            // We have not yet sent a final response. If all responses have been received,
            // select the "best" response and forward it to the client.

            int bestResponse = 999;
            ProxyResponseContextACI bestResponseContext = null;

            boolean[] canDetachACI = new boolean[acis.length]; // Flag which ACIs we can detach from

            for (int i = 0; i < acis.length; i++) {
                Object activity = acis[i].getActivity();
                if (activity instanceof ClientTransaction || activity instanceof NullActivity) {
                    ProxyResponseContextACI proxyACI = asSbbActivityContextInterface(acis[i]);
                    int statusCode = proxyACI.getResponseStatusCode();

                    // The proxy only creates null ACIs with response status code set - therefore if
                    // response status code is still zero then this isn't our null activity, so ignore it.
                    if (statusCode == 0 && activity instanceof NullActivity) continue;

                    if (statusCode < 200) {
                        // Still no final response received for at least one txn.
                        // Do nothing, txns will eventually time out
                        // and we will have our final responses then.
                        finer("processResponseContexts: still waiting for all final responses");
                        return;
                    }
                    else {
                        // use lowest response code in each class,
                        // and lowest response class if no 6xx responses
                        if (statusCode >= 600) {
                            // 6xx class responses have priority
                            if ((bestResponse >= 600 && statusCode < bestResponse) || (bestResponse < 600)) {
                                // this is the new best 6xx response
                                bestResponse = statusCode;
                                bestResponseContext = proxyACI;
                            }
                        }
                        else if (statusCode >= 300 && statusCode < bestResponse && bestResponse/100 != 6) {
                            bestResponse = statusCode;
                            bestResponseContext = proxyACI;
                        }
                        // A final response has been received, so OK to detach this ACI.
                        canDetachACI[i] = true;
                    }
                }
            }

            // All responses have come in. We haven't yet sent any final responses
            // on the server transaction, so pick the "best" one and send it.

            assert bestResponseContext != null;

            if (isTraceable(TraceLevel.FINER))
                finer("processResponseContexts: selected best final response: " + bestResponse);

            for (int i = 0; i < acis.length; i++) {
                if (canDetachACI[i]) acis[i].detach(getSbbLocalObject());
            }

            getProxyResponseListener().forwardResponse(generateErrorResponse(bestResponseContext));
        }
    }

    protected ProxyResponseContextACI addErrorToResponseContext(Response response) {
        // Store the error response info in a null ACI, so that we can access it after
        // all client transactions have ended.
        return addErrorToResponseContext(response.getStatusCode(),
                ((ToHeader)response.getHeader(ToHeader.NAME)).getTag());
    }

    protected ProxyResponseContextACI addErrorToResponseContext(int statusCode, String toTag) {
        // Create a null ACI for the error response - this will be used to generate
        // the "best" response when all responses have come in.
        try {
            NullActivity n = getNullActivityFactory().createNullActivity();
            ProxyResponseContextACI aci = asSbbActivityContextInterface(getNullACIFactory().getActivityContextInterface(n));
            aci.setResponseStatusCode(statusCode);
            aci.setErrorResponseToTag(toTag);
            aci.attach(getSbbLocalObject());
            return aci;
        } catch (UnrecognizedActivityException e) {
            throw new RuntimeException(e);
        }
    }

    protected Response generateErrorResponse(ProxyResponseContextACI proxyACI) {
        ServerTransaction st = getProxyResponseListener().getServerTransaction();
        if (st == null) throw new RuntimeException("no server transaction attached, cannot generate response");

        try {
            Response response = getSipMessageFactory().createResponse(proxyACI.getResponseStatusCode(), st.getRequest());

            // Preserve to-tag from response, if any
            ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
            String receivedTag = proxyACI.getErrorResponseToTag();
            if (receivedTag != null && to.getTag() != null) {
                to.setTag(receivedTag);
            }

            // TODO fill-in any additional headers from response context

            return response;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    protected void handleOptions(RequestEvent optionsEvent) {
        if (isTraceable(TraceLevel.FINE)) fine("received OPTIONS directed to this proxy, generate OK response");
        sendResponse(optionsEvent.getServerTransaction(), Response.OK);
    }

    // Called from inside proxy only
    protected void sendResponse(Response response) {
        if (response.getStatusCode() >= 200) {
            setProxyState(PROXY_SENT_FINAL_RESPONSE);
        }

        ProxyResponseListener listener = getProxyResponseListener();
        // Listener may be null if this is a stateless response, just call proxy directly.
        if (listener != null) listener.forwardResponse(response);
        else forwardResponse(response);
    }

    protected ActivityContextInterface getServerTransactionACI() {
        ActivityContextInterface[] acis = getSbbContext().getActivities();
        for (int i = 0; i < acis.length; i++) {
            if (acis[i].getActivity() instanceof ServerTransaction)
                return acis[i];
        }
        return null;
    }

    protected void setTimerC(ProxyResponseContextACI responseContextACI) {
        if (isTraceable(TraceLevel.FINER))
            finer("setTimerC: fire in " + timerC + " secs");
        TimerID id = getTimerFacility().setTimer(responseContextACI, null, System.currentTimeMillis() + (timerC * 1000), defaultTimerOptions);
        responseContextACI.setTimerC(id);
    }

    protected void updateTimerC(ProxyResponseContextACI responseContextACI, Response response) {
        int status = response.getStatusCode();
        if (status >= 101 && status <= 199) {
            // reset timer C
            if (isTraceable(TraceLevel.FINER))
                finer("updateTimerC: got provisional response, reset timer C, fire in " + timerC + " secs");
            getTimerFacility().cancelTimer(responseContextACI.getTimerC());
            TimerID newTimer = getTimerFacility().setTimer(responseContextACI, null, System.currentTimeMillis() + (timerC * 1000), defaultTimerOptions);
            responseContextACI.setTimerC(newTimer);
        }
        else if (status >= 200) {
            // cancel timer C
            if (isTraceable(TraceLevel.FINER))
                finer("updateTimerC: got final response, cancel Timer C");
            getTimerFacility().cancelTimer(responseContextACI.getTimerC());
        }
    }

    // Not for CANCELs or ACKs
    protected ProxyResponseContextACI forwardRequest(Request request) {
        // forward request, if necessary attach to client transaction to receive
        // responses
        if (isTraceable(TraceLevel.FINEST)) finest("forwarding request:\n" + request);
        // Send request on a new client transaction, and attach so we get responses
        ProxyResponseContextACI proxyACI = null;
        try {
            ClientTransaction ct = getSipProvider().getNewClientTransaction(request);
            ActivityContextInterface aci = getSipACIFactory().getActivityContextInterface(ct);
            proxyACI = asSbbActivityContextInterface(aci);
            proxyACI.attach(getSbbLocalObject());
            ct.sendRequest();
            return proxyACI;
        } catch (Throwable e) {
            // Create a "fake" response ACI with 500 error code - processResponseContexts
            // will pick this up and send the 500 response if all client txns fail
            warn("unable to send request, returning 503 Service Unavailable", e);
            if (proxyACI != null) proxyACI.detach(getSbbLocalObject());
            return addErrorToResponseContext(Response.SERVICE_UNAVAILABLE, null);
        }
    }

    public abstract boolean getForking();
    public abstract void setForking(boolean forking);

    public abstract boolean getUseLocationService();
    public abstract void setUseLocationService(boolean useLocationService);

    public abstract boolean getRecordRoute();
    public abstract void setRecordRoute(boolean recordRoute);

    /**
     * The SBB that we send responses through. If deployed standalone,
     * this is the Proxy SBB itself.
     */
    public abstract void setProxyResponseListener(ProxyResponseListener listener);
    public abstract ProxyResponseListener getProxyResponseListener();

    public abstract int getProxyState();
    public abstract void setProxyState(int state);

    /** True if proxied transaction was an INVITE transaction */
    public abstract boolean getInvite();
    public abstract void setInvite(boolean invite);

    public abstract ProxyResponseContextACI asSbbActivityContextInterface(ActivityContextInterface aci);

    // Child relation for Location Service
    public abstract ChildRelation getLocationServiceChildRelation();
    private LocationService getLocationService() throws CreateException {
        // Ensure we only ever create at most one location service child SBB
        ChildRelation locationChildRelation = getLocationServiceChildRelation();
        if (locationChildRelation.isEmpty()) {
            return (LocationService) locationChildRelation.create();
        }
        else {
            return (LocationService) locationChildRelation.iterator().next();
        }
    }

    private int timerC = DEFAULT_TIMERC; // seconds
    private ProxyRouter router;

    private static final int PROXY_INIT = 0;
    private static final int PROXY_WAIT_FOR_FINAL_RESPONSE = 1;
    private static final int PROXY_SENT_FINAL_RESPONSE = 2;

    private static final int DEFAULT_TIMERC = 180; // seconds
    private static final TimerOptions defaultTimerOptions = new TimerOptions();
}
