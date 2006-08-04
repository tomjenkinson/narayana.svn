/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.  All rights reserved. 
 * See the copyright.txt in the distribution for a full listing 
 * of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License, v. 2.0.
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * v. 2.0 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 * 
 * (C) 2005-2006,
 * @author JBoss Inc.
 */
package com.arjuna.wst.messaging;

import com.arjuna.webservices.SoapFault;
import com.arjuna.webservices.base.processors.ActivatedObjectProcessor;
import com.arjuna.webservices.logging.WSTLogger;
import com.arjuna.webservices.wsaddr.AddressingContext;
import com.arjuna.webservices.wsarj.ArjunaContext;
import com.arjuna.webservices.wsarj.InstanceIdentifier;
import com.arjuna.webservices.wsat.NotificationType;
import com.arjuna.webservices.wsat.CoordinatorInboundEvents;
import com.arjuna.webservices.wsat.client.ParticipantClient;
import com.arjuna.webservices.wsat.processors.CoordinatorProcessor;
import com.arjuna.wsc.messaging.MessageId;

/**
 * The Coordinator processor.
 * @author kevin
 */
public class CoordinatorProcessorImpl extends CoordinatorProcessor
{
    /**
     * The activated object processor.
     */
    private final ActivatedObjectProcessor activatedObjectProcessor = new ActivatedObjectProcessor() ;

    /**
     * Activate the coordinator.
     * @param coordinator The coordinator.
     * @param identifier The identifier.
     */
    public void activateCoordinator(final CoordinatorInboundEvents coordinator, final String identifier)
    {
        activatedObjectProcessor.activateObject(coordinator, identifier) ;
    }

    /**
     * Deactivate the coordinator.
     * @param coordinator The coordinator.
     */
    public void deactivateCoordinator(final CoordinatorInboundEvents coordinator)
    {
        activatedObjectProcessor.deactivateObject(coordinator) ;
    }
    
    /**
     * Get the coordinator with the specified identifier.
     * @param instanceIdentifier The coordinator identifier.
     * @return The coordinator or null if not known.
     */
    private CoordinatorInboundEvents getCoordinator(final InstanceIdentifier instanceIdentifier)
    {
        final String identifier = (instanceIdentifier != null ? instanceIdentifier.getInstanceIdentifier() : null) ;
        return (CoordinatorInboundEvents)activatedObjectProcessor.getObject(identifier) ;
    }
    
    /**
     * Aborted.
     * @param aborted The aborted notification.
     * @param addressingContext The addressing context.
     * @param arjunaContext The arjuna context.
     * 
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.aborted_1 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.aborted_1] - Unexpected exception thrown from aborted:
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.aborted_2 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.aborted_2] - Aborted called on unknown coordinator: {0}
     */
    public void aborted(final NotificationType aborted, final AddressingContext addressingContext,
        final ArjunaContext arjunaContext)
    {
        final InstanceIdentifier instanceIdentifier = arjunaContext.getInstanceIdentifier() ;
        final CoordinatorInboundEvents coordinator = getCoordinator(instanceIdentifier) ;
        
        if (coordinator != null)
        {
            try
            {
                coordinator.aborted(aborted, addressingContext, arjunaContext) ;
            }
            catch (final Throwable th)
            {
                if (WSTLogger.arjLoggerI18N.isWarnEnabled())
                {
                    WSTLogger.arjLoggerI18N.warn("com.arjuna.wst.messaging.CoordinatorProcessorImpl.aborted_1", th) ; 
                }
            }
        }
        else if (WSTLogger.arjLoggerI18N.isWarnEnabled())
        {
            WSTLogger.arjLoggerI18N.warn("com.arjuna.wst.messaging.CoordinatorProcessorImpl.aborted_2", new Object[] {instanceIdentifier}) ; 
        }
    }
    
    /**
     * Committed.
     * @param committed The committed notification.
     * @param addressingContext The addressing context.
     * @param arjunaContext The arjuna context.
     * 
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.committed_1 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.committed_1] - Unexpected exception thrown from committed:
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.committed_2 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.committed_2] - Aborted called on unknown coordinator: {0}
     */
    public void committed(final NotificationType committed, final AddressingContext addressingContext,
        final ArjunaContext arjunaContext)
    {
        final InstanceIdentifier instanceIdentifier = arjunaContext.getInstanceIdentifier() ;
        final CoordinatorInboundEvents coordinator = getCoordinator(instanceIdentifier) ;
        
        if (coordinator != null)
        {
            try
            {
                coordinator.committed(committed, addressingContext, arjunaContext) ;
            }
            catch (final Throwable th)
            {
                if (WSTLogger.arjLoggerI18N.isWarnEnabled())
                {
                    WSTLogger.arjLoggerI18N.warn("com.arjuna.wst.messaging.CoordinatorProcessorImpl.committed_1", th) ; 
                }
            }
        }
        else if (WSTLogger.arjLoggerI18N.isWarnEnabled())
        {
            WSTLogger.arjLoggerI18N.warn("com.arjuna.wst.messaging.CoordinatorProcessorImpl.committed_2", new Object[] {instanceIdentifier}) ; 
        }
    }
    
    /**
     * Prepared.
     * @param prepared The prepared notification.
     * @param addressingContext The addressing context.
     * @param arjunaContext The arjuna context.
     * 
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.prepared_1 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.prepared_1] - Unexpected exception thrown from prepared:
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.prepared_2 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.prepared_2] - Aborted called on unknown coordinator: {0}
     */
    public void prepared(final NotificationType prepared, final AddressingContext addressingContext,
        final ArjunaContext arjunaContext)
    {
        final InstanceIdentifier instanceIdentifier = arjunaContext.getInstanceIdentifier() ;
        final CoordinatorInboundEvents coordinator = getCoordinator(instanceIdentifier) ;
        
        if (coordinator != null)
        {
            try
            {
                coordinator.prepared(prepared, addressingContext, arjunaContext) ;
            }
            catch (final Throwable th)
            {
                if (WSTLogger.arjLoggerI18N.isWarnEnabled())
                {
                    WSTLogger.arjLoggerI18N.warn("com.arjuna.wst.messaging.CoordinatorProcessorImpl.prepared_1", th) ; 
                }
            }
        }
        else
        {
            if (WSTLogger.arjLoggerI18N.isWarnEnabled())
            {
                WSTLogger.arjLoggerI18N.warn("com.arjuna.wst.messaging.CoordinatorProcessorImpl.prepared_2", new Object[] {instanceIdentifier}) ; 
            }
            // Assume participant is durable
            sendRollback(addressingContext, arjunaContext) ;
        }
    }
    
    /**
     * Read only.
     * @param readOnly The read only notification.
     * @param addressingContext The addressing context.
     * @param arjunaContext The arjuna context.
     * 
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.readOnly_1 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.readOnly_1] - Unexpected exception thrown from readOnly:
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.readOnly_2 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.readOnly_2] - Aborted called on unknown coordinator: {0}
     */
    public void readOnly(final NotificationType readOnly, final AddressingContext addressingContext,
        final ArjunaContext arjunaContext)
    {
        final InstanceIdentifier instanceIdentifier = arjunaContext.getInstanceIdentifier() ;
        final CoordinatorInboundEvents coordinator = getCoordinator(instanceIdentifier) ;
        
        if (coordinator != null)
        {
            try
            {
                coordinator.readOnly(readOnly, addressingContext, arjunaContext) ;
            }
            catch (final Throwable th)
            {
                if (WSTLogger.arjLoggerI18N.isWarnEnabled())
                {
                    WSTLogger.arjLoggerI18N.warn("com.arjuna.wst.messaging.CoordinatorProcessorImpl.readOnly_1", th) ; 
                }
            }
        }
        else if (WSTLogger.arjLoggerI18N.isWarnEnabled())
        {
            WSTLogger.arjLoggerI18N.warn("com.arjuna.wst.messaging.CoordinatorProcessorImpl.readOnly_2", new Object[] {instanceIdentifier}) ; 
        }
    }
    
    /**
     * Replay.
     * @param replay The replay notification.
     * @param addressingContext The addressing context.
     * @param arjunaContext The arjuna context.
     * 
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.replay_1 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.replay_1] - Unexpected exception thrown from replay:
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.replay_2 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.replay_2] - Aborted called on unknown coordinator: {0}
     */
    public void replay(final NotificationType replay, final AddressingContext addressingContext,
        final ArjunaContext arjunaContext)
    {
        final InstanceIdentifier instanceIdentifier = arjunaContext.getInstanceIdentifier() ;
        final CoordinatorInboundEvents coordinator = getCoordinator(instanceIdentifier) ;
        
        if (coordinator != null)
        {
            try
            {
                coordinator.replay(replay, addressingContext, arjunaContext) ;
            }
            catch (final Throwable th)
            {
                if (WSTLogger.arjLoggerI18N.isWarnEnabled())
                {
                    WSTLogger.arjLoggerI18N.warn("com.arjuna.wst.messaging.CoordinatorProcessorImpl.replay_1", th) ; 
                }
            }
        }
        else
        {
            if (WSTLogger.arjLoggerI18N.isWarnEnabled())
            {
                WSTLogger.arjLoggerI18N.warn("com.arjuna.wst.messaging.CoordinatorProcessorImpl.replay_2", new Object[] {instanceIdentifier}) ; 
            }
            // Assume participant is durable
            sendRollback(addressingContext, arjunaContext) ;
        }
    }

    /**
     * SOAP Fault.
     * @param soapFault The SOAP fault notification.
     * @param addressingContext The addressing context.
     * @param arjunaContext The arjuna context.
     * 
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.soapFault_1 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.soapFault_1] - Unexpected exception thrown from soapFault: 
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.soapFault_2 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.soapFault_2] - SoapFault called on unknown coordinator: {0}
     */
    public void soapFault(final SoapFault fault, final AddressingContext addressingContext,
        final ArjunaContext arjunaContext)
    {
        final InstanceIdentifier instanceIdentifier = arjunaContext.getInstanceIdentifier() ;
        final CoordinatorInboundEvents coordinator = getCoordinator(instanceIdentifier) ;

        if (coordinator != null)
        {
            try
            {
                coordinator.soapFault(fault, addressingContext, arjunaContext) ;
            }
            catch (final Throwable th)
            {
                if (WSTLogger.arjLoggerI18N.isWarnEnabled())
                {
                    WSTLogger.arjLoggerI18N.warn("com.arjuna.wst.messaging.CoordinatorProcessorImpl.soapFault_1", th) ;
                }
            }
        }
        else
        {
            if (WSTLogger.arjLoggerI18N.isWarnEnabled())
            {
                WSTLogger.arjLoggerI18N.warn("com.arjuna.wst.messaging.CoordinatorProcessorImpl.soapFault_2", new Object[] {instanceIdentifier}) ;
            }
        }
    }
    
    /**
     * Send a rollback message.
     * 
     * @param addressingContext The addressing context.
     * @param arjunaContext The arjuna context.
     * 
     * @message com.arjuna.wst.messaging.CoordinatorProcessorImpl.sendRollback_1 [com.arjuna.wst.messaging.CoordinatorProcessorImpl.sendRollback_1] - Unexpected exception while sending Rollback
     */
    private void sendRollback(final AddressingContext addressingContext, final ArjunaContext arjunaContext)
    {
        // KEV add check for recovery
        final String messageId = MessageId.getMessageId() ;
        final AddressingContext responseAddressingContext = AddressingContext.createNotificationContext(addressingContext, messageId) ;
        final InstanceIdentifier instanceIdentifier = arjunaContext.getInstanceIdentifier() ;
        try
        {
            ParticipantClient.getClient().sendRollback(responseAddressingContext, instanceIdentifier) ;
        }
        catch (final Throwable th)
        {
            if (WSTLogger.arjLoggerI18N.isDebugEnabled())
            {
                WSTLogger.arjLoggerI18N.debug("com.arjuna.wst.messaging.CoordinatorProcessorImpl.sendRollback_1", th) ;
            }
        }
    }
}
