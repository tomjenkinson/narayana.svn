package com.jboss.transaction.txinterop.webservices.bainterop;

import java.io.IOException;

import com.arjuna.webservices.SoapFault;
import org.oasis_open.docs.ws_tx.wscoor._2006._06.CoordinationContextType;
import com.arjuna.wsc11.messaging.MessageId;
import com.arjuna.webservices11.wsaddr.AddressingHelper;
import com.jboss.transaction.txinterop.webservices.bainterop.client.SyncParticipantClient;

import javax.xml.ws.addressing.AddressingProperties;

/**
 * The participant stub.
 */
public class SyncParticipantStub implements ParticipantStub
{
    /***
     * The participant stub singletong.
     */
    private static final ParticipantStub PARTICIPANT_STUB = new SyncParticipantStub() ;
    
    /**
     * Get the participant stub singleton.
     * @return The participant stub singleton.
     */
    public static ParticipantStub getParticipantStub()
    {
        return PARTICIPANT_STUB ;
    }
    
    /**
     * Send a cancel request.
     * @param serviceURI The target service URI.
     * @param coordinationContext The coordination context.
     * @throws SoapFault For any errors.
     * @throws IOException for any transport errors.
     */
    public void cancel(final String serviceURI, final CoordinationContextType coordinationContext)
        throws SoapFault, IOException
    {
        final String messageId = MessageId.getMessageId() ;
        final AddressingProperties addressingProperties = AddressingHelper.createRequestContext(serviceURI, messageId) ;
        
        SyncParticipantClient.getClient().sendCancel(coordinationContext, addressingProperties) ;
    }
    
    /**
     * Send a exit request.
     * @param serviceURI The target service URI.
     * @param coordinationContext The coordination context.
     * @throws SoapFault For any errors.
     * @throws IOException for any transport errors.
     */
    public void exit(final String serviceURI, final CoordinationContextType coordinationContext)
        throws SoapFault, IOException
    {
        final String messageId = MessageId.getMessageId() ;
        final AddressingProperties addressingProperties = AddressingHelper.createRequestContext(serviceURI, messageId) ;
        
        SyncParticipantClient.getClient().sendExit(coordinationContext, addressingProperties) ;
    }
    
    /**
     * Send a fail request.
     * @param serviceURI The target service URI.
     * @param coordinationContext The coordination context.
     * @throws SoapFault For any errors.
     * @throws IOException for any transport errors.
     */
    public void fail(final String serviceURI, final CoordinationContextType coordinationContext)
        throws SoapFault, IOException
    {
        final String messageId = MessageId.getMessageId() ;
        final AddressingProperties addressingProperties = AddressingHelper.createRequestContext(serviceURI, messageId) ;
        
        SyncParticipantClient.getClient().sendFail(coordinationContext, addressingProperties) ;
    }
    
    /**
     * Send a cannotComplete request.
     * @param serviceURI The target service URI.
     * @param coordinationContext The coordination context.
     * @throws SoapFault For any errors.
     * @throws IOException for any transport errors.
     */
    public void cannotComplete(final String serviceURI, final CoordinationContextType coordinationContext)
        throws SoapFault, IOException
    {
        final String messageId = MessageId.getMessageId() ;
        final AddressingProperties addressingProperties = AddressingHelper.createRequestContext(serviceURI, messageId) ;
        
        SyncParticipantClient.getClient().sendCannotComplete(coordinationContext, addressingProperties) ;
    }
    
    /**
     * Send a participantCompleteClose request.
     * @param serviceURI The target service URI.
     * @param coordinationContext The coordination context.
     * @throws SoapFault For any errors.
     * @throws IOException for any transport errors.
     */
    public void participantCompleteClose(final String serviceURI, final CoordinationContextType coordinationContext)
        throws SoapFault, IOException
    {
        final String messageId = MessageId.getMessageId() ;
        final AddressingProperties addressingProperties = AddressingHelper.createRequestContext(serviceURI, messageId) ;
        
        SyncParticipantClient.getClient().sendParticipantCompleteClose(coordinationContext, addressingProperties) ;
    }
    
    /**
     * Send a coordinatorCompleteClose request.
     * @param serviceURI The target service URI.
     * @param coordinationContext The coordination context.
     * @throws SoapFault For any errors.
     * @throws IOException for any transport errors.
     */
    public void coordinatorCompleteClose(final String serviceURI, final CoordinationContextType coordinationContext)
        throws SoapFault, IOException
    {
        final String messageId = MessageId.getMessageId() ;
        final AddressingProperties addressingProperties = AddressingHelper.createRequestContext(serviceURI, messageId) ;
        
        SyncParticipantClient.getClient().sendCoordinatorCompleteClose(coordinationContext, addressingProperties) ;
    }
    
    /**
     * Send a unsolicitedComplete request.
     * @param serviceURI The target service URI.
     * @param coordinationContext The coordination context.
     * @throws SoapFault For any errors.
     * @throws IOException for any transport errors.
     */
    public void unsolicitedComplete(final String serviceURI, final CoordinationContextType coordinationContext)
        throws SoapFault, IOException
    {
        final String messageId = MessageId.getMessageId() ;
        final AddressingProperties addressingProperties = AddressingHelper.createRequestContext(serviceURI, messageId) ;
        
        SyncParticipantClient.getClient().sendUnsolicitedComplete(coordinationContext, addressingProperties) ;
    }
    
    /**
     * Send a compensate request.
     * @param serviceURI The target service URI.
     * @param coordinationContext The coordination context.
     * @throws SoapFault For any errors.
     * @throws IOException for any transport errors.
     */
    public void compensate(final String serviceURI, final CoordinationContextType coordinationContext)
        throws SoapFault, IOException
    {
        final String messageId = MessageId.getMessageId() ;
        final AddressingProperties addressingProperties = AddressingHelper.createRequestContext(serviceURI, messageId) ;
        
        SyncParticipantClient.getClient().sendCompensate(coordinationContext, addressingProperties) ;
    }
    
    /**
     * Send a compensationFail request.
     * @param serviceURI The target service URI.
     * @param coordinationContext The coordination context.
     * @throws SoapFault For any errors.
     * @throws IOException for any transport errors.
     */
    public void compensationFail(final String serviceURI, final CoordinationContextType coordinationContext)
        throws SoapFault, IOException
    {
        final String messageId = MessageId.getMessageId() ;
        final AddressingProperties addressingProperties = AddressingHelper.createRequestContext(serviceURI, messageId) ;
        
        SyncParticipantClient.getClient().sendCompensationFail(coordinationContext, addressingProperties) ;
    }
    
    /**
     * Send a participantCancelCompletedRace request.
     * @param serviceURI The target service URI.
     * @param coordinationContext The coordination context.
     * @throws SoapFault For any errors.
     * @throws IOException for any transport errors.
     */
    public void participantCancelCompletedRace(final String serviceURI, final CoordinationContextType coordinationContext)
        throws SoapFault, IOException
    {
        final String messageId = MessageId.getMessageId() ;
        final AddressingProperties addressingProperties = AddressingHelper.createRequestContext(serviceURI, messageId) ;
        
        SyncParticipantClient.getClient().sendParticipantCancelCompletedRace(coordinationContext, addressingProperties) ;
    }
    
    /**
     * Send a messageLossAndRecovery request.
     * @param serviceURI The target service URI.
     * @param coordinationContext The coordination context.
     * @throws SoapFault For any errors.
     * @throws IOException for any transport errors.
     */
    public void messageLossAndRecovery(final String serviceURI, final CoordinationContextType coordinationContext)
        throws SoapFault, IOException
    {
        final String messageId = MessageId.getMessageId() ;
        final AddressingProperties addressingProperties = AddressingHelper.createRequestContext(serviceURI, messageId) ;
        
        SyncParticipantClient.getClient().sendMessageLossAndRecovery(coordinationContext, addressingProperties) ;
    }
    
    /**
     * Send a mixedOutcome request.
     * @param serviceURI The target service URI.
     * @param coordinationContext The coordination context.
     * @throws SoapFault For any errors.
     * @throws IOException for any transport errors.
     */
    public void mixedOutcome(final String serviceURI, final CoordinationContextType coordinationContext)
        throws SoapFault, IOException
    {
        final String messageId = MessageId.getMessageId() ;
        final AddressingProperties addressingProperties = AddressingHelper.createRequestContext(serviceURI, messageId) ;
        
        SyncParticipantClient.getClient().sendMixedOutcome(coordinationContext, addressingProperties) ;
    }
}
