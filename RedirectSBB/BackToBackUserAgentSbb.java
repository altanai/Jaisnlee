package com.opencloud.slee.services.sip.b2bua;

import com.opencloud.slee.services.sip.common.OCSipSbb;
import com.opencloud.slee.services.sip.location.LocationService;
import com.opencloud.slee.services.sip.location.Registration;
import com.opencloud.slee.services.sip.location.*;

import net.java.slee.resource.sip.CancelRequestEvent;
import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.DialogForkedEvent;

import com.opencloud.javax.sip.slee.OCSleeSipProvider;

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


public abstract class BackToBackUserAgentSbb extends OCSipSbb {
	
	//-------------------from and to fetching variables
	String caller=null;
	String caller2=null;
	
	String callee=null;
	String callee2=null;
	
	URI uripass;
	URI uripasslookup;
	
	//--------------------get message for the entire event
	String inviteeventmsg=null;
	String inviteeventmsgreplaced = null;
	String invitemsgfrom=null;
	
	//------------------------- retry parameters 
	String retry="no";
	
	//------------------------- pass parameters 
	String pass="pass";
		
	//------------------------- dialog parameters
	DialogActivity incomingDialog;
	DialogActivity outgoingDialog;
	ActivityContextInterface outgoingDialogACI;
	ActivityContextInterface incomingDialogACI;	
	
    protected String getTraceMessageType() { return "B2BUA"; }

    public void setSbbContext(SbbContext context) 
    {
        super.setSbbContext(context);
       
        try {
            Context myEnv = (Context) new InitialContext().lookup("java:comp/env");
            domains = parseDomains((String)myEnv.lookup("domains"));
        } catch (NamingException e) {
            severe("Could not set SBB context", e);
        }
    }


    public void onInitialInvite(RequestEvent event, ActivityContextInterface aci) {
    	
    //	if (isTraceable(TraceLevel.FINEST)) finest("............"+ event.getRequest()+"............ " );
   																													//	 finest("CNS request ............................................................................." + event.getRequest());
        OCSleeSipProvider p = (OCSleeSipProvider) event.getSource();

        ServerTransaction st = event.getServerTransaction();

        setInitialServerTransaction(aci);

/*............................................................................................*/
        inviteeventmsg = event.getRequest().toString();

        if (isTraceable(TraceLevel.FINEST)) finest("...................... inviteeventmsg " + inviteeventmsg);
        
        int indexstart = inviteeventmsg.indexOf("From:",0);
		int start= indexstart+7;
		
		int indexend = inviteeventmsg.indexOf(";tag=",0);
		int end = indexend-11;

		
																														if (isTraceable(TraceLevel.FINEST)) finest(" indexstart > " + indexstart +" start > "+ start );
																														if (isTraceable(TraceLevel.FINEST)) finest(" indexend > " + indexend +" end > "+ end );
		
		invitemsgfrom= inviteeventmsg.substring(start,end);
																														if (isTraceable(TraceLevel.FINEST)) finest("...................... invitemsgfrom " + invitemsgfrom);
	


	             	//...........................................
					try{
					
				    uripass = getSipAddressFactory().createURI("sip:3@10.1.5.15:5070");
				    
		           		 finest("............. uripass "+ uripass);
					}
					catch(Exception e){
						
				    	finest("............. address factory exception ");
			    	}
					//............................................
/*.............................................................................................*/  						//  if (isTraceable(TraceLevel.FINEST)) finest("................................received initial INVITE:\n" + event.getRequest());
    																													if (isTraceable(TraceLevel.FINEST)) finest("................................received initial INVITE");
       // inviteneventmsg= event.getRequest();
        
        try 
        {

           																											finer("initializing UAS dialog");
           // DialogActivity incomingDialog = (DialogActivity) getSleeSipProvider().getNewDialog(st);
          			incomingDialog = (DialogActivity) getSleeSipProvider().getNewDialog(st);						 finer("initializing UAC dialog");
           // DialogActivity outgoingDialog = getSleeSipProvider().getNewDialog(incomingDialog, true);
					outgoingDialog = getSleeSipProvider().getNewDialog(incomingDialog, true);

           // ActivityContextInterface outgoingDialogACI = getSipACIFactory().getActivityContextInterface(outgoingDialog);
           			outgoingDialogACI = getSipACIFactory().getActivityContextInterface(outgoingDialog);
           // ActivityContextInterface incomingDialogACI = getSipACIFactory().getActivityContextInterface(incomingDialog);
           			incomingDialogACI = getSipACIFactory().getActivityContextInterface(incomingDialog);
           			
            incomingDialogACI.attach(getSbbLocalObject());
            outgoingDialogACI.attach(getSbbLocalObject());


            getSbbContext().maskEvent(OUTGOING_EVENT_MASK, outgoingDialogACI);


            setIncomingDialog(incomingDialogACI);
            setOutgoingDialog(outgoingDialogACI);

            forwardRequest(st, outgoingDialog, true);
        } 
        catch (Exception e)
        {
            																									warn("failed to forward initial request", e);
            sendErrorResponse(st, Response.SERVER_INTERNAL_ERROR);
        }
    }



    private void forwardRequest(ServerTransaction st, DialogActivity out, boolean initial) throws SipException 
    {

       
        Request incomingRequest = st.getRequest();
        Request outgoingRequest = out.createRequest(incomingRequest);

        if (initial) {

            URI requestURI = incomingRequest.getRequestURI();
            
            //........................................
            callee= requestURI.toString();
            
            URI requestingURI = outgoingRequest.getRequestURI();
            caller= requestingURI.toString();
            //..........................................
            
            if (isLocalDomain(requestURI, domains) && pass.equalsIgnoreCase("skip"))
            {
	                if (isTraceable(TraceLevel.FINE)) fine(requestURI + " is in a local domain, lookup address in location service");
	                URI registeredAddress = lookupRegisteredAddress(requestURI);
	                
	                if (registeredAddress == null) 
	                {
	                    if (isTraceable(TraceLevel.FINE)) fine("no registered address found for " + requestURI);
	                    
	                    sendErrorResponse(st, Response.TEMPORARILY_UNAVAILABLE);
	                    
	                    return;
	                }
	                if (isTraceable(TraceLevel.FINE)) fine("found registered address: " + registeredAddress);
	                
	                //........................................
		                      
		            //URI requestingURIpass = URI.createURI("sip:yoyo@10.1.5.15:6064");
		            //URI requestingURIpass = ("sip:yoyo@10.1.5.15:6064");
		           
		            //URI requestingURIpass = javax.sip.address.Address.getURI('sip:yoyo@10.1.5.15:6064');
		            //URI registeredAddresspass = lookupRegisteredAddress(requestingURIpass);
		            
		            //SipProvider provider=null;
		            
		            
	
		           // String localAddress = provider.getListeningPoints()[0].getIPAddress(); 
		            //finest("............. local Address"+ localAddress);	
		            
		            //static URI requestingURIpass = javax.sip.address.AddressFactory.createURI("yoyo@10.1.5.15:6064");
		            //URI registeredAddresspass = lookupRegisteredAddress(requestingURIpass);
		            
		            //..........................................

	                if (isTraceable(TraceLevel.FINEST))
	                {
	                	finest("............................");
	                	finest(" caller "+ invitemsgfrom);
	                	finest(" callee (requestURI) "+ callee);
	                	finest(" callee (registered address) "+ registeredAddress);
	                	finest(" callee pass ( uri pass) "+ uripass);
	                	finest(" callee pass ( uri pass lookup) "+ uripasslookup);
	                	finest("............................");
	                }

							            
	                outgoingRequest.setRequestURI(registeredAddress);
	        }
	        
	        else  if (isLocalDomain(uripass, domains) && pass.equalsIgnoreCase("pass")) 
            {
	                if (isTraceable(TraceLevel.FINE)) fine(uripass + " is in a local domain, lookup address in location service");
	                uripasslookup = lookupRegisteredAddress(uripass);
	                
	                if (uripasslookup == null) 
	                {
	                    if (isTraceable(TraceLevel.FINE)) fine("no registered address found for " + uripasslookup);
	                    
	                    sendErrorResponse(st, Response.TEMPORARILY_UNAVAILABLE);
	                    
	                    return;
	                }
	                if (isTraceable(TraceLevel.FINE)) fine("found registered address: " + uripasslookup);

	                if (isTraceable(TraceLevel.FINEST))
	                {
	                	finest("............................");
	                	finest(" caller "+ invitemsgfrom);
	                	finest(" callee pass ( uri pass) "+ uripass);
	                	finest(" callee pass ( uri pass lookup) "+ uripasslookup);
	                	finest("............................");
	                }

							            
	                outgoingRequest.setRequestURI(uripasslookup);
	        }
	        
	        
	        
	        else 
	        {
	           if (isTraceable(TraceLevel.FINE)) fine(requestURI + " is outside our domain, forwarding");
	        }
        }

        if (isTraceable(TraceLevel.FINEST)) finest(".............................forwarding request on dialog " + out + ":\n" + outgoingRequest);
        
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

   

    private void forwardResponse(DialogActivity in, DialogActivity out, ClientTransaction ct, Response receivedResponse) throws SipException 
    {
      
        ServerTransaction st = in.getAssociatedServerTransaction(ct); 
        	
        if (st == null) throw new SipException("could not find associated server transaction");
        
        Response outgoingResponse = out.createResponse(st, receivedResponse);
        																																							if (isTraceable(TraceLevel.FINEST)) finest("...........................forwarding response on dialog " + out + ":\n" + outgoingResponse);
        
        try 
        {
            st.sendResponse(outgoingResponse);
           
        } 
        catch (InvalidArgumentException e) 
        {
            throw new SipException("invalid response", e);
        }
    }

    private void forwardReliableResponse(DialogActivity in, DialogActivity out, ClientTransaction ct, Response receivedResponse) throws SipException 
    {

        ServerTransaction st = in.getAssociatedServerTransaction(ct);
        
        if (st == null) throw new SipException("could not find associated server transaction");
      
        Response outgoingResponse = out.createResponse(st, receivedResponse);
        																																							if (isTraceable(TraceLevel.FINEST)) finest("forwarding reliable response on dialog " + out + ":\n" + outgoingResponse);
       
        out.sendReliableProvisionalResponse(outgoingResponse);
    }

    private void sendErrorResponse(ServerTransaction st, int statusCode) {
    	if (isTraceable(TraceLevel.FINEST)) finest(".......................... sedning error response   ");
        try {
            Response response = getSipMessageFactory().createResponse(statusCode, st.getRequest());
            st.sendResponse(response);
           
					/*		try{
        					
        					if (isTraceable(TraceLevel.FINEST)) finest(".......................... seelping for 30 seconds now   ");
        					Thread.sleep(30000);
        					}
        					catch ( Exception e ){
        						if (isTraceable(TraceLevel.FINEST)) finest("...........................exception in thread sleep ");
        					}
                             //....................................
           					// if(retry.equals("yes"))
          					// {
          						try{
          							
          						if (isTraceable(TraceLevel.FINEST)) finest(".......................... retryin to connect again  ");
            					forwardRequest(st, outgoingDialog, true);
          						}
          						catch(Exception e){
          							if (isTraceable(TraceLevel.FINEST)) finest("...........................exception forwarding response  ");
          						}
           					// }
           					 //....................................
           			 */
           
        } catch (Exception e) {
            warn("failed to send error response", e);
        }
        				
        
    }

///////////////////////////////////////////////////////////////////////////////////////
    
    private void processMidDialogRequest(RequestEvent event, ActivityContextInterface dialogACI) {
     																																   								if (isTraceable(TraceLevel.FINEST)) finest("..........................received mid-dialog request on dialog " + dialogACI.getActivity() + ":\n" + event.getRequest());
        try
        {
            ActivityContextInterface peerACI = getPeerDialog(dialogACI);
            forwardRequest(event.getServerTransaction(), (DialogActivity)peerACI.getActivity(), false);
        } 
        catch (SipException e)
        {
            warn("failed to forward request", e);
            sendErrorResponse(event.getServerTransaction(), Response.SERVER_INTERNAL_ERROR);
        }
    }


    private void processResponse(ResponseEvent event, ActivityContextInterface aci) {
        Response response = event.getResponse();
      																																 								 if (isTraceable(TraceLevel.FINEST)) finest(".......................received response on dialog " + aci.getActivity() + ":\n" + event.getResponse());
        if (getCancelled())
        {
                if (response.getStatusCode() >= 400 && Request.INVITE.equals(((CSeqHeader)response.getHeader(CSeqHeader.NAME)).getMethod())) 
                {
	               																																					 if (isTraceable(TraceLevel.FINER)) finer("no need to forward response from cancelled INVITE");
	                setCancelled(false); // reset
	                return;
           		 }
        }
        
        try
        {
            ActivityContextInterface peerACI = getPeerDialog(aci);
            forwardResponse((DialogActivity)aci.getActivity(), (DialogActivity)peerACI.getActivity(), event.getClientTransaction(), response);
            
        } 
        catch (SipException e) 
        {
            warn("failed to forward response", e);
        }
    }

    private void processReliableResponse(ResponseEvent event, ActivityContextInterface aci) {
        Response response = event.getResponse();
   																																  	   if (isTraceable(TraceLevel.FINEST)) finest("..................................received reliable response on dialog " + aci.getActivity() + ":\n" + event.getResponse());

        try
        {
            ActivityContextInterface peerACI = getPeerDialog(aci);
            forwardReliableResponse((DialogActivity)aci.getActivity(), (DialogActivity)peerACI.getActivity(), event.getClientTransaction(), response);
        } 
        catch (SipException e) 
        {
            warn("failed to forward response", e);
        }
    }

    private ActivityContextInterface getPeerDialog(ActivityContextInterface aci) throws SipException 
    {
        if (aci.equals(getIncomingDialog())) return getOutgoingDialog();
        if (aci.equals(getOutgoingDialog())) return getIncomingDialog();
        throw new SipException("could not find peer dialog");
    }
//////////////////////////////////////////////////////////////////////////////////////


 @SuppressWarnings("unchecked")
    private URI lookupRegisteredAddress(URI publicAddress)
    {
    	if (isTraceable(TraceLevel.FINEST)) finest(".............................lookup Registration .......................altnai");
       
        Registration reg = null;
       
       
        try 
        {
            LocationService ls = (LocationService) getLocationServiceChildRelation().create();
            
           // String addrs=publicAddress.toString();
            //	if (isTraceable(TraceLevel.FINEST)) finest("..............................addrs -> " + addrs);
            //try{
            //Contact c2 = (Contact) reg.getContact(addrs);
             //	if (isTraceable(TraceLevel.FINEST)) finest(".............................c2 -> " + c2.toString());
            //}
            //catch(Exception e)
            //{
            //	if (isTraceable(TraceLevel.FINEST)) finest(".............................exception in contact....................");
            //}
            
            reg = ls.getRegistration(getCanonicalAddress(publicAddress));
        	
            
            
            
            
        }
        catch (CreateException e) 
        {
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


///////////////////////////////////////////////////////////////////////////////////////

    public void onCancel(CancelRequestEvent event, ActivityContextInterface aci) 
    {
       																																								 if (isTraceable(TraceLevel.FINEST)) finest(".........................received CANCEL request:\n" + event.getRequest());
       																																								 // Which leg did the CANCEL arrive on? Send cancel on the opposite leg.   // If it arrived on the initial server transaction, send on outgoing leg.
        handleCancel(event, aci.equals(getInitialServerTransaction()) || aci.equals(getIncomingDialog()) ? getOutgoingDialog() : getIncomingDialog());
    }

    public void onTransactionTimeout(TimeoutEvent event, ActivityContextInterface aci) 
    {
        if (event.isServerTransaction()) return;
        
        ClientTransaction ct = event.getClientTransaction();
       																																								 if (isTraceable(TraceLevel.FINER)) finer("transaction " + ct + " timed out");
        if (getCancelled())
        {
            // is this an error response for a cancelled INVITE? If so, consume the response here, RA has// already responded to upstream host.
            if (Request.INVITE.equals(ct.getRequest().getMethod()))
            {
                																																					if (isTraceable(TraceLevel.FINER)) finer("no need to forward timeout from cancelled INVITE");
                setCancelled(false); // reset
            }
        }
    }

    /**
     * Initialise a new entity tree for the new forked dialog
     */ 
    private void handleFork(DialogForkedEvent event, ActivityContextInterface aci) {
        
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

    public void onDialogForked(DialogForkedEvent event, ActivityContextInterface aci) {
        finest("dialog forked");
        handleFork(event, aci);
    }
    
    
/////////////////////////////////////////////////////////////////////////////////////////////
    // Responses

    public void on1xxResponse(ResponseEvent event, ActivityContextInterface aci) {
        Response response = event.getResponse();
        if (response.getHeader(RSeqHeader.NAME) != null) {
            processReliableResponse(event, aci);
        }
        																																				if (isTraceable(TraceLevel.FINEST)) finest("................................retry yes");
        else
        {
        
        	processResponse(event, aci);
        }
       
    }

    public void on2xxResponse(ResponseEvent event, ActivityContextInterface aci) {
        OCSleeSipProvider p = (OCSleeSipProvider) event.getSource();
        																																				if (isTraceable(TraceLevel.FINEST)) finest("................................retry no");
        //..........................
       
       retry="no";
       
       //....................... 
        processResponse(event, aci);
        
        
    }

    public void on3xxResponse(ResponseEvent event, ActivityContextInterface aci) {
        processResponse(event, aci);
    }

    public void on4xxResponse(ResponseEvent event, ActivityContextInterface aci) {
    	
    
       
        processResponse(event, aci);
    }

    public void on5xxResponse(ResponseEvent event, ActivityContextInterface aci) {
        processResponse(event, aci);
    }

    public void on6xxResponse(ResponseEvent event, ActivityContextInterface aci) {
        processResponse(event, aci);
    }

    // Mid-dialog requests

    public void onAck(RequestEvent event, ActivityContextInterface aci) {
        processMidDialogRequest(event, aci);
    }

    public void onBye(RequestEvent event, ActivityContextInterface aci) {
        OCSleeSipProvider p = (OCSleeSipProvider) event.getSource();
        processMidDialogRequest(event, aci);
    }

    public void onReInvite(RequestEvent event, ActivityContextInterface aci) {
        processMidDialogRequest(event, aci);
    }

    public void onPrack(RequestEvent event, ActivityContextInterface aci) {
        processMidDialogRequest(event, aci);
    }

    public void onUpdate(RequestEvent event, ActivityContextInterface aci) {
        processMidDialogRequest(event, aci);
    }

    public void onInfo(RequestEvent event, ActivityContextInterface aci) {
        processMidDialogRequest(event, aci);
    }

    public void onSubscribe(RequestEvent event, ActivityContextInterface aci) {
        processMidDialogRequest(event, aci);
    }

    public void onNotify(RequestEvent event, ActivityContextInterface aci) {
        processMidDialogRequest(event, aci);
    }

    public void onPublish(RequestEvent event, ActivityContextInterface aci) {
        processMidDialogRequest(event, aci);
    }

    public void onRefer(RequestEvent event, ActivityContextInterface aci) {
        processMidDialogRequest(event, aci);
    }

    public void onMessage(RequestEvent event, ActivityContextInterface aci) {
        processMidDialogRequest(event, aci);
    }

    public void onUnknownRequest(RequestEvent event, ActivityContextInterface aci) {
        processMidDialogRequest(event, aci);
    }

//////////////////////////////////////////////////////////////////////////////////////

    public abstract void setInitialServerTransaction(ActivityContextInterface aci);
    public abstract ActivityContextInterface getInitialServerTransaction();

    public abstract void setIncomingDialog(ActivityContextInterface aci);
    public abstract ActivityContextInterface getIncomingDialog();

    public abstract void setOutgoingDialog(ActivityContextInterface aci);
    public abstract ActivityContextInterface getOutgoingDialog();

    public abstract void setCancelled(boolean cancelled);
    public abstract boolean getCancelled();

    public abstract ChildRelation getLocationServiceChildRelation();

    private String[] domains;

    private static final String[] OUTGOING_EVENT_MASK = new String[] { "DialogForked" };
    
    
    
    public interface Contact {
        public String getContactURI();
        public FlowID getFlowID();

        public float getQValue();
        public long getExpiryAbsolute();
        public long getExpiryDelta();
        public String getCallId();
        public int getCSeq();
    }
}
