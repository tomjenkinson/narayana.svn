/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. 
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
package com.arjuna.wsc11.tests.junit;

import java.util.HashMap;
import java.util.Map;

import com.arjuna.webservices11.wscoor.processors.ActivationCoordinatorProcessor;
import com.arjuna.webservices11.wscoor.CoordinationConstants;
import com.arjuna.wsc.tests.TestUtil;
import com.arjuna.wsc.AlreadyRegisteredException;
import com.arjuna.wsc.InvalidProtocolException;
import com.arjuna.wsc.InvalidStateException;
import com.arjuna.wsc.NoActivityException;
import com.arjuna.wsc11.tests.TestRegistrar;
import com.arjuna.wsc11.tests.TestUtil11;
import org.oasis_open.docs.ws_tx.wscoor._2006._06.CreateCoordinationContextType;
import org.oasis_open.docs.ws_tx.wscoor._2006._06.CreateCoordinationContextResponseType;
import org.oasis_open.docs.ws_tx.wscoor._2006._06.CoordinationContext;
import org.oasis_open.docs.ws_tx.wscoor._2006._06.CoordinationContextType;

import javax.xml.ws.addressing.AddressingProperties;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import javax.xml.ws.wsaddressing.W3CEndpointReferenceBuilder;

public class TestActivationCoordinatorProcessor extends
        ActivationCoordinatorProcessor
{
    private Map messageIdMap = new HashMap() ;

    public CreateCoordinationContextResponseType createCoordinationContext(final CreateCoordinationContextType createCoordinationContext,
        final AddressingProperties addressingProperties)
    {
        final String messageId = addressingProperties.getMessageID().getURI().toString() ;
        synchronized(messageIdMap)
        {
            messageIdMap.put(messageId, new CreateCoordinationContextDetails(createCoordinationContext, addressingProperties)) ;
            messageIdMap.notifyAll() ;
        }
        // we have to return a value so lets cook one up

        CreateCoordinationContextResponseType createCoordinationContextResponseType = new CreateCoordinationContextResponseType();
        CoordinationContext coordinationContext = new CoordinationContext();
        coordinationContext.setCoordinationType(createCoordinationContext.getCoordinationType());
        coordinationContext.setExpires(createCoordinationContext.getExpires());
        String identifier = nextIdentifier();
        CoordinationContextType.Identifier identifierInstance = new CoordinationContextType.Identifier();
        identifierInstance.setValue(identifier);
        coordinationContext.setIdentifier(identifierInstance);
        W3CEndpointReferenceBuilder builder = new W3CEndpointReferenceBuilder();
        builder.serviceName(CoordinationConstants.REGISTRATION_SERVICE_QNAME);
        builder.endpointName(CoordinationConstants.REGISTRATION_ENDPOINT_QNAME);
        builder.address(TestUtil.PROTOCOL_COORDINATOR_SERVICE);
        W3CEndpointReference registrationService = builder.build();
        coordinationContext.setRegistrationService(TestUtil11.getRegistrationEndpoint(identifier));
        createCoordinationContextResponseType.setCoordinationContext(coordinationContext);

        return createCoordinationContextResponseType;
    }

    public CreateCoordinationContextDetails getCreateCoordinationContextDetails(final String messageId, long timeout)
    {
        final long endTime = System.currentTimeMillis() + timeout ;
        synchronized(messageIdMap)
        {
            long now = System.currentTimeMillis() ;
            while(now < endTime)
            {
                final CreateCoordinationContextDetails details = (CreateCoordinationContextDetails)messageIdMap.remove(messageId) ;
                if (details != null)
                {
                    return details ;
                }
                try
                {
                    messageIdMap.wait(endTime - now) ;
                }
                catch (final InterruptedException ie) {} // ignore
                now = System.currentTimeMillis() ;
            }
            final CreateCoordinationContextDetails details = (CreateCoordinationContextDetails)messageIdMap.remove(messageId) ;
            if (details != null)
            {
                return details ;
            }
        }
        throw new NullPointerException("Timeout occurred waiting for id: " + messageId) ;
    }

    public static class CreateCoordinationContextDetails
    {
        private final CreateCoordinationContextType createCoordinationContext ;
        private final AddressingProperties addressingProperties ;

        CreateCoordinationContextDetails(final CreateCoordinationContextType createCoordinationContext,
            final AddressingProperties addressingProperties)
        {
            this.createCoordinationContext = createCoordinationContext ;
            this.addressingProperties = addressingProperties ;
        }

        public CreateCoordinationContextType getCreateCoordinationContext()
        {
            return createCoordinationContext ;
        }

        public AddressingProperties getAddressingProperties()
        {
            return addressingProperties ;
        }
    }

    private static int nextIdentifier = 0;

    private synchronized String nextIdentifier()
    {
        int value = nextIdentifier++;

        return Integer.toString(value);
    }
}