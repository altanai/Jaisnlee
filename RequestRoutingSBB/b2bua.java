/* 
* Developer : Altanai 
* Date : 2013
* website : http://altanaitelecom.wordpress.com 
* Project : Request Rerouting
*/
 
package com.opencloud.slee.services.sip.b2bua;

import com.opencloud.slee.services.sip.common.OCSipSbb;
import com.opencloud.slee.services.sip.location.LocationService;
import com.opencloud.slee.services.sip.location.Registration;
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


/**
 * Back To Back User Agent Servic eBulding Block 
 * JAIN SIP 1.2 Resource Adapter Required to run this in Open Cloud Telecom application server (Rhino TAS)
 */
public abstract class BackToBackUserAgentSbb extends OCSipSbb {
    protected String getTraceMessageType() { return "B2BUA"; }

    DataSource ds ;
    Connection conn ;

   String Callee_URI = null ;
	 String Service_callscreening = null;
	 String Service_callrouting = null ;
	 String Service_sms = null;
	 String Caller_URI = null;
	 String Block_URI = null ;
	 String Reroute_URI = null ;
	 String  inviteeventmsg = null ;
	 URI    uripass = null ;
	 URI uripasslookup = null;
	 ResultSet rs =null;

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
     * @throws SipException
     * @throws Exception
     */
    public void onInitialInvite(RequestEvent event, ActivityContextInterface aci)  {

    	// ACI is the server transaction activity
        ServerTransaction st = event.getServerTransaction();
        // Stay attached to server transaction, in case we get a CANCEL.
        // Save ACI so we can distinguish CANCEL events arriving on the server transaction
        // (cancelled initial INVITE) from events arriving on a dialog (cancelled re-INVITE).
        setInitialServerTransaction(aci);

//..................................................................for the global connections of database............//

    try{

        	Context myEnv = (Context) new InitialContext().lookup("java:resource");
          ds = (DataSource) myEnv.lookup("jdbc/ExternalDataSource");
          finest("...................everything is working fine ...");

    }
		 catch (NamingException ne)
		{

		severe("Could not set SBB context", ne);
		finest("....................Exception with datasource");
		}

//................................TO get from URI and To get TO URI.....get Caller and Callee URI...//

		 Callee_URI = st.getRequest().getRequestURI().toString();

			finest(" calleee "+ Callee_URI);

		  inviteeventmsg = event.getRequest().toString();

		 	int indexstart = inviteeventmsg.indexOf("From:",0);
			int start= indexstart;

			int indexend = inviteeventmsg.indexOf(";tag=",0);
			int end = indexend;

			finest(" start " + start +" : end "+ end);
			Caller_URI = inviteeventmsg.substring(start,end);

			finest("Caller_URI.length()"+Caller_URI.length() );

			Caller_URI=Caller_URI.substring(7, Caller_URI.length()-6);

			finest(" caller "+Caller_URI );
//....................................................................................................//

        if (isTraceable(TraceLevel.FINEST)) finest("received initial INVITE:\n" + event.getRequest());

      try{
            // Create the dialogs representing the incoming and outgoing call legs.
            finer("initializing UAS dialog");
            DialogActivity incomingDialog = (DialogActivity) getSleeSipProvider().getNewDialog(st);
            finer("initializing UAC dialog");
            DialogActivity outgoingDialog = getSleeSipProvider().getNewDialog(incomingDialog, true);

            // Obtain the dialog activity contexts and attach to them
            ActivityContextInterface outgoingDialogACI = getSipACIFactory().getActivityContextInterface(outgoingDialog);
            ActivityContextInterface incomingDialogACI = getSipACIFactory().getActivityContextInterface(incomingDialog);
            incomingDialogACI.attach(getSbbLocalObject());
            outgoingDialogACI.attach(getSbbLocalObject());

   //.....................................................................................

            finest("finished attaching ACI");
            // Mask forked response events. Another root SBB entity will be created to process them.
            getSbbContext().maskEvent(OUTGOING_EVENT_MASK, outgoingDialogACI);

            // Record which dialog is which, so we can find the peer dialog when forwarding messages between dialogs.
            setIncomingDialog(incomingDialogACI);
            setOutgoingDialog(outgoingDialogACI);


			finest("finished creating dialogues");
//...................................................................block to fetch the services he/she has subscribed for....//

            try {

				//conn = ds.getConnection();
    		 	jdbcconnection();
    		 	String sql = "SELECT * FROM services where usersipuri=?" ;
    		 	PreparedStatement stam = conn.prepareStatement(sql);
    		 	stam.setString(1, Callee_URI);

  				finest("sql :"+ sql);
  				finest("Callee_URI :"+Callee_URI );
  				finest("Prepared stsment  :"+ stam);

    		 	rs = stam.executeQuery();

    		 	while(rs.next())
    		 	{
    		 		Service_callscreening = rs.getString(3);
    		 		Service_callrouting = rs.getString(4);
    		 		Service_sms = rs.getString(5);
    		 		//System.out.println(rs.getString(1));
    		 		finest("service callscreening : "+ Service_callscreening);
    		 		finest("service callrerouting : "+Service_callrouting);
    		 		finest("service_sms : "+Service_sms);

    		 	}
    		 	rs.close();
    		 	stam.close();
    		 	jdbcclosed();
            }
            catch (Exception e)
            {
			finest("exception in services");
    		}

            if ( Service_callscreening.equalsIgnoreCase("yes")&& Service_callrouting.equalsIgnoreCase("yes")){

			finest(" yes & yes ");
       		 //block to get into the call screening block........executed

	       		 			jdbcconnection();
	       		 			String sql = "SELECT * FROM callscreening where calleesipuri=?" ;
	       		 			PreparedStatement stam = conn.prepareStatement(sql);
	       		 			stam.setString(1, Callee_URI);
	       		 			ResultSet rs = stam.executeQuery();

	       		 			while(rs.next())
	       		 			{
	       		 				Block_URI = rs.getString(4);

	       		 			}

       		 				if(Block_URI.contains(Caller_URI))
       		 				{
									finest(".................... screen match found  ..");
									//throw new Exception(" caller screened ");
									Response response = getSipMessageFactory().createResponse(403, st.getRequest());
									st.sendResponse(response);
       		 				}


       		 				else
       		 			    {
       		 					// Run the code for the rerouting  request.// before it need to fetch the callee re-routing destinations.

       		 					rs.close();
       			 				stam.close();
       			 				jdbcclosed();

       					 			jdbcconnection();
       					 			String sql1 = "SELECT * FROM callrerouting where calleesipuri=?" ;
       					 			PreparedStatement stam1 = conn.prepareStatement(sql1);
       					 			stam1.setString(1, Callee_URI);
       					 			ResultSet rs1 = stam1.executeQuery();

       					 			while(rs.next())
       					 			{
       					 				Reroute_URI = rs1.getString(6);

       					 			}

       					 		// Reroute the sip reuqest tp Reroute_URI........
                        uripass = getSipAddressFactory().createURI(Reroute_URI);

		       					    finest("............. uripass "+ uripass);

		       					 		rs1.close();
		       					 		stam1.close();
		       					 		jdbcclosed();
		       					 		forwardRequestReroute(st, outgoingDialog, true);
       			 				}


       		}

            if ( Service_callscreening.equalsIgnoreCase("yes")&& Service_callrouting.equalsIgnoreCase("no"))
			{
    			finest(" yes & no ");
       		 //block to get into the call screening block........executed

       		 		try
					{
						finest("into the yes and no block")
       		 			jdbcconnection();
					
       		 			String sql = "SELECT callersipuri FROM callscreening where calleesipuri=?" ;
       		 			PreparedStatement stam = conn.prepareStatement(sql);
       		 			stam.setString(1, Callee_URI);

						finest("sql :"+ sql);
						finest("Callee_URI :"+Callee_URI );
						finest("Prepared stsment  :"+ stam);

       		 			ResultSet rs = stam.executeQuery();

       		 			while(rs.next())

       		 			{
       		 				Block_URI = rs.getString(1);
							finest(" Blocked_URI :"+Block_URI);

       		 			}
       		 			if (Block_URI.contains(Caller_URI))
       		 			{
       		 				finest(".................... screen match found  ..");
							Response response = getSipMessageFactory().createResponse(403, st.getRequest());
							st.sendResponse(response);
       		 			}
       		 			else
       		 			{
							finest(".................... no match found  ..");
       		 				forwardRequest(st, outgoingDialog, true);
       		 			}
       		 				rs.close();
       		 				stam.close();
       		 				jdbcclosed();
       		 		}
       		 		catch (Exception e)
       				{
       					finest("problem in yes no call screenig ");

       				}

       	 }

         if (Service_callscreening.equalsIgnoreCase("no") && Service_callrouting.equalsIgnoreCase("yes"))
       	 {

       			    finest(" no & yes ");
       	 			jdbcconnection();
       	 			String sql = "SELECT * FROM callrerouting where calleesipuri=?" ;
       	 			PreparedStatement stam = conn.prepareStatement(sql);
       	 			stam.setString(1, Callee_URI);
       	 			ResultSet rs = stam.executeQuery();

					finest("sql :"+ sql);
					finest("Callee_URI :"+Callee_URI );
					finest("Prepared stsment  :"+ stam);

       	 			while(rs.next())
       	 			{
       	 				Reroute_URI = rs.getString(6);
						finest(" Reroute_URI :"+Reroute_URI);

       	 			}
       	 			uripass = getSipAddressFactory().createURI(Reroute_URI);
       	 			finest("............. uripass "+ uripass);

				 		rs.close();
				 		stam.close();
				 		jdbcclosed();
				 		forwardRequestReroute(st, outgoingDialog, true);
       	 }

         if (Service_callscreening.equalsIgnoreCase("no") && Service_callrouting.equalsIgnoreCase("no"))
         {
			 finest(" no & no ");
        	 forwardRequest(st, outgoingDialog, true);
         }

      }

      catch(Exception e)

      {
    	  finest("exception in main try block");

      }
    }

    /**
     * A CANCEL request was received for the initial INVITE or re-INVITE.
     * CANCELs are hop-by-hop, so instead of forwarding the request, we
     * must create a new CANCEL on the appropriate client transaction.
     */
    public void onCancel(CancelRequestEvent event, ActivityContextInterface aci) {
        if (isTraceable(TraceLevel.FINEST)) finest("received CANCEL request:\n" + event.getRequest());
        // Which leg did the CANCEL arrive on? Send cancel on the opposite leg.
        // If it arrived on the initial server transaction, send on outgoing leg.
        handleCancel(event, aci.equals(getInitialServerTransaction()) || aci.equals(getIncomingDialog())
                ? getOutgoingDialog() : getIncomingDialog());
    }

    /**
     * A forked 1xx response has arrived, creating a new dialog.<p>
     * This is an initial event, we setup a new B2BUA entity tree to handle
     * the new dialog.
     */
    public void onDialogForked(DialogForkedEvent event, ActivityContextInterface aci) {
        finest("dialog forked");
        handleFork(event, aci);
    }

    // Responses

    public void on1xxResponse(ResponseEvent event, ActivityContextInterface aci) {
        Response response = event.getResponse();
        if (response.getHeader(RSeqHeader.NAME) != null) {
            processReliableResponse(event, aci);
        }
        else processResponse(event, aci);
    }

    public void on2xxResponse(ResponseEvent event, ActivityContextInterface aci) {
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

    // Transaction Timeout - don't need to forward a timeout response, just consume timeout (see RFC4320).
    // Forwarding timeout responses is unnecessary as upstream hosts will have already timed out.
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

    /**
     * Initialise a new entity tree for the new forked dialog
     */
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

    // Helpers

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
            // Just forward the ACK statelessly - don't need to remember transaction state
            out.sendAck(outgoingRequest);
        }
        else {
            // Send the request on the dialog activity
            ClientTransaction ct = out.sendRequest(outgoingRequest);
            // Record an association with the original server transaction, so we can retrieve it
            // when forwarding the response.
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

    public void jdbcconnection (){

		try
	 	 {
	  conn = ds.getConnection();

	     }
		catch (Exception e)
		{
finest("Exception in jdbc open");
		}

    	}
		public  void jdbcclosed()
		{

				try
				{
		   		    conn.close();
				}
		       catch (SQLException e)
		       {
					// TODO Auto-generated catch block
					finest("Exception in jdbc closed");
				}
		}

		private void forwardRequestReroute(ServerTransaction st, DialogActivity out, boolean initial) throws SipException
	    {


	        Request incomingRequest = st.getRequest();
	        Request outgoingRequest = out.createRequest(incomingRequest);

	        if (initial) {

	            URI requestURI = incomingRequest.getRequestURI();

	            //........................................
	            //callee= requestURI.toString();

	            URI requestingURI = outgoingRequest.getRequestURI();
	            //caller= requestingURI.toString();
	            //..........................................

	            if (isLocalDomain(requestURI, domains) )
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



		                if (isTraceable(TraceLevel.FINEST))
		                {
		                	finest("............................");
		                	finest(" caller "+ Caller_URI);
		                	finest(" callee (requestURI) ");
		                	finest(" callee (registered address) "+ registeredAddress);
		                	finest(" callee pass ( uri pass) "+ uripass);
		                	finest(" callee pass ( uri pass lookup) ");
		                	finest("............................");
		                }


		                outgoingRequest.setRequestURI(registeredAddress);
		        }

		        else  if (isLocalDomain(uripass, domains))
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
}







