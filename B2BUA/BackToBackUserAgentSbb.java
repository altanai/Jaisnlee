/* 
* Developer : Altanai 
* Date : 2013
* website : http://altanaitelecom.wordpress.com 
* Project : Request Rerouting
*/
 

package com.opencloud.slee.services.sip.b2bua;

import com.opencloud.javax.sip.slee.OCSleeSipProvider;
import com.opencloud.javax.sip.slee.OCSipActivityContextInterfaceFactory;
import com.opencloud.javax.sip.header.OCHeaderFactory;
import com.opencloud.javax.sip.Endpoint;

import com.opencloud.slee.services.sip.common.OCSipSbb;
import com.opencloud.slee.services.sip.location.LocationService;
import com.opencloud.slee.services.sip.location.Registration;
import com.opencloud.slee.services.sip.location.*;

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

/**
 * Back To Back User Agent Service Bulding Block 
 * JAIN SIP 1.2 Resource Adapter Required to run this in Open Cloud Telecom application server (Rhino TAS)
 */
public abstract class BackToBackUserAgentSbb extends OCSipSbb {

    protected String getTraceMessageType() { return "B2BUA"; }

    public void setSbbContext(SbbContext context) 
    {
        super.setSbbContext(context);
       
        try {
            Context myEnv = (Context) new InitialContext().lookup("java:comp/env");
            domains = parseDomains((String)myEnv.lookup("domains"));

//............................................................................................
		         providerName = (String) myEnv.lookup("sipProviderName");
																																																					
             factoryName = (String) myEnv.lookup("sipACIFactoryName");
																																																					
            sipSbbInterface2 = (OCSleeSipProvider) myEnv.lookup(providerName);
																																																					
            sipACIFactory2 = (OCSipActivityContextInterfaceFactory) myEnv.lookup(factoryName);

																																																					
//............................................................................................


        } catch (NamingException e) {
            severe("Could not set SBB context", e);
        }
    }


    public void onInitialInvite(RequestEvent event, ActivityContextInterface aci) {
    	
   																																																				 //if (isTraceable(TraceLevel.FINEST)) finest("............"+ event.getRequest()+"............ " );
   																																																				//	 finest("CNS request ..............................." + event.getRequest());
        OCSleeSipProvider p = (OCSleeSipProvider) event.getSource();

        ServerTransaction st = event.getServerTransaction();

        setInitialServerTransaction(aci);
																																																					if (isTraceable(TraceLevel.FINEST)) finest("............ sipACIFactory2 "+ sipACIFactory2);
/*...................................get from uri.........................................................*/
        inviteeventmsg = event.getRequest().toString();
        
            int indexstart = inviteeventmsg.indexOf("From:",0);
		    int start= indexstart+7;
		
		    int indexend = inviteeventmsg.indexOf(";tag=",0);
		    int end = indexend-11;
		
		    invitemsgfrom= inviteeventmsg.substring(start,end);
/*.....................................create new uri.......................................................*/	
																																																				if (isTraceable(TraceLevel.FINEST)) finest("...................... invitemsgfrom " + invitemsgfrom);
					try
					{
				       uripass = getSipAddressFactory().createURI("sip:11@10.1.5.15:5070"); 
		           																																													finest("............. uripass "+ uripass);
					}
					catch(Exception e)
					{
						
				    	finest("............. address factory exception ");
					}

/*.............................................................................................*/  			 
 																																																		//  if (isTraceable(TraceLevel.FINEST)) finest(".........................received initial INVITE:\n" + event.getRequest());
  																																																			if (isTraceable(TraceLevel.FINEST)) finest("......................received initial INVITE");
        
        try 
        {

           																																																	finer("initializing UAS dialog");
   
          	incomingDialog = (DialogActivity) getSleeSipProvider().getNewDialog(st);												                                                                         finer("initializing UAC dialog");
         
		    outgoingDialog = getSleeSipProvider().getNewDialog(incomingDialog, true);

 
           	outgoingDialogACI = getSipACIFactory().getActivityContextInterface(outgoingDialog);

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

        if (initial) 
		{

            URI requestURI = incomingRequest.getRequestURI();
			//callee = requestURI.toString();
            
            if (isLocalDomain(requestURI, domains))
            {
            	    
	                																																								if (isTraceable(TraceLevel.FINE)) fine(requestURI + " is in a local domain, lookup address in location service");
	                URI registeredAddress = lookupRegisteredAddress(requestURI);
	                
	                if (registeredAddress ==  null) 
	                {
	                    																																							if (isTraceable(TraceLevel.FINE)) fine("no registered address found for " + requestURI);
	                    
	                    sendErrorResponse(st, Response.TEMPORARILY_UNAVAILABLE);
	                    
	                    return;
	                }
	                																																								if (isTraceable(TraceLevel.FINE)) fine("found registered address: " + registeredAddress);
	                

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
	        else 
	        {
	         		if (isTraceable(TraceLevel.FINE)) fine(requestURI + " is outside our domain, forwarding");
	        }
        }

																																												  //if (isTraceable(TraceLevel.FINEST)) finest(".................forwarding request on dialog " + out + ":\n" + outgoingRequest);
																																													if (isTraceable(TraceLevel.FINEST)) finest(".............................forwarding request on dialog");
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
        																																										if (isTraceable(TraceLevel.FINEST)) finest("........................forwarding response on dialog " + out + ":\n" + outgoingResponse);
																																														if (isTraceable(TraceLevel.FINEST)) finest("........................forwarding response on dialog " + out );
        
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
        																																										if (isTraceable(TraceLevel.FINEST)) finest("...........................forwarding reliable response on dialog " + out + ":\n" + outgoingResponse);
																																														if (isTraceable(TraceLevel.FINEST)) finest("...........................forwarding reliable response on dialog " + out );
       
        out.sendReliableProvisionalResponse(outgoingResponse);
    }

    private void sendErrorResponse(ServerTransaction st, int statusCode) {
    																																										   	if (isTraceable(TraceLevel.FINEST)) finest(".......................... sedning error response   ");
        try {
            Response response = getSipMessageFactory().createResponse(statusCode, st.getRequest());
            st.sendResponse(response);
           
        } catch (Exception e) {
            warn("failed to send error response", e);
        }
        				
        
    }

///////////////////////////////////////////////////////////////////////////////////////
    
    private void processMidDialogRequest(RequestEvent event, ActivityContextInterface dialogACI) {
     																																										      //if (isTraceable(TraceLevel.FINEST)) finest("...................received mid-dialog request on dialog " + dialogACI.getActivity() + ":\n" + event.getRequest());
																																										          if (isTraceable(TraceLevel.FINEST)) finest("...................received mid-dialog request on dialog " + dialogACI.getActivity());
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
      																																 									      //if (isTraceable(TraceLevel.FINEST)) finest(".................received response on dialog " + aci.getActivity() + ":\n" + event.getResponse());
																																											        if (isTraceable(TraceLevel.FINEST)) finest(".................received response on dialog " + aci.getActivity());
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
   																																  											  // if (isTraceable(TraceLevel.FINEST)) finest("..................................received reliable response on dialog " + aci.getActivity() + ":\n" + event.getResponse());
																																														if (isTraceable(TraceLevel.FINEST)) finest("..................................received reliable response on dialog " + aci.getActivity());

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
    																																                      if (isTraceable(TraceLevel.FINEST)) finest(".............................lookup Registration .......................");
       
        Registration reg = null;
 
        try 
        {
            LocationService ls = (LocationService) getLocationServiceChildRelation().create();
            
            reg = ls.getRegistration(getCanonicalAddress(publicAddress));
          
        }
        catch (CreateException e) 
        {
            warn("could not create location service child SBB", e); 
        }

        if (reg == null) return null;

        List<Registration.Contact> contacts = (List<Registration.Contact>) reg.getContacts();
        float maxQ = -1f; // q-values are from 0.0 - 1.0
        String best = null;

        for (Registration.Contact contact : contacts) 
				{
            if (contact.getQValue() > maxQ) 
						{
                best = contact.getContactURI();
                maxQ = contact.getQValue();
            }
        }

        if (best == null) return null;

        try 
				{
            return getSleeSipProvider().getAddressFactory().createURI(best);
        } 
				catch (ParseException e) 
				{
            warn("failed to parse URI: " + best, e);
            return null;
        }
    }


///////////////////////////////////////////////////////////////////////////////////////

    public void onCancel(CancelRequestEvent event, ActivityContextInterface aci) 
	{
   
      																					                                                      // Which leg did the CANCEL arrive on? Send cancel on the opposite leg. If it arrived on the initial server transaction, send on outgoing leg.
        handleCancel(event, aci.equals(getInitialServerTransaction()) || aci.equals(getIncomingDialog()) ? getOutgoingDialog() : getIncomingDialog());
    }

    public void onTransactionTimeout(TimeoutEvent event, ActivityContextInterface aci) 
    {
        if (event.isServerTransaction()) return;
        
        ClientTransaction ct = event.getClientTransaction();
       																																								     if (isTraceable(TraceLevel.FINER)) finer("transaction " + ct + " timed out");
        if (getCancelled())
        {

            if (Request.INVITE.equals(ct.getRequest().getMethod()))
            {
                																																					if (isTraceable(TraceLevel.FINER)) finer("no need to forward timeout from cancelled INVITE");
                setCancelled(false); // reset
            }
        }
    }

    private void handleFork(DialogForkedEvent event, ActivityContextInterface aci) {
        
        aci.detach(getSbbLocalObject());
        ResponseEvent responseEvent = event.getResponseEvent();
        ServerTransaction st;
        DialogActivity uac;
       																																						     	if (isTraceable(TraceLevel.FINEST)) finest("received forked response on dialog " + aci.getActivity() + ":\n" + responseEvent.getResponse());
        try
		    {
            uac = event.getNewDialog();
            ActivityContextInterface uacACI = getSipACIFactory().getActivityContextInterface(uac);
            uacACI.attach(getSbbLocalObject());
            setOutgoingDialog(uacACI);
            st = uac.getAssociatedServerTransaction(responseEvent.getClientTransaction());
        } 
				catch (Exception e) 
				{
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


    private void handleCancel(CancelRequestEvent cancelEvent, ActivityContextInterface dialogACI)
    {
       
        if (getSleeSipProvider().acceptCancel(cancelEvent, false)) 
        {
            
            DialogActivity dialog = (DialogActivity) dialogACI.getActivity();
            
            																																									if (isTraceable(TraceLevel.FINEST)) finest("sending CANCEL on dialog " + dialog);        
            try 
            {
                dialog.sendCancel();
                setCancelled(true); 
            } catch (SipException e) {
                warn("failed to send CANCEL", e);
            }
        }
        
    }

    public void onDialogForked(DialogForkedEvent event, ActivityContextInterface aci) {
        finest("dialog forked");
        handleFork(event, aci);
    }
    
    

    //------------------------------------------------- Responses---------------------------------------------------------------------------------------

    public void on1xxResponse(ResponseEvent event, ActivityContextInterface aci) {
																																																	if (isTraceable(TraceLevel.FINEST)) finest(".............resp : 1xx response ");
        Response response = event.getResponse();
        if (response.getHeader(RSeqHeader.NAME) != null) 
		{
            processReliableResponse(event, aci);
        }
        																																				
        else
        {
        
        	processResponse(event, aci);
        }
       
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
//-------------------------------------error reposen custom----------------------------------------------------------------------------------
    public void sendbusyresponse(Object event, ActivityContextInterface aci)
	{
		RequestEvent requestEvent = (RequestEvent) event;
		sendErrorResponse(requestEvent.getServerTransaction(), Response.BUSY_HERE);
	}

     public void senddeclineresponse(Object event, ActivityContextInterface aci)
	{
		RequestEvent requestEvent = (RequestEvent) event;
		sendErrorResponse(requestEvent.getServerTransaction(), Response.DECLINE);
	}

//-------------------------------------------------------------------------------------------------------------------------------------------

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


//------------------------------------------------------------------------------------------------------------------------------------------------

	
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
	
	//------------------------- pass parameters 
	String pass="skip";
		
	//------------------------- dialog parameters
	DialogActivity incomingDialog;
	DialogActivity outgoingDialog;
	ActivityContextInterface outgoingDialogACI;
	ActivityContextInterface incomingDialogACI;	

  // ------------- env variables 
  String providerName;
  String factoryName;
    OCSleeSipProvider sipSbbInterface2;
    OCSipActivityContextInterfaceFactory sipACIFactory2;
	Endpoint endpoint2;
    
  
}
