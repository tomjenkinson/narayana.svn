/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. 
 * See the copyright.txt in the distribution for a full listing 
 * of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 * 
 * (C) 2005-2006,
 * @author JBoss Inc.
 */
package com.arjuna.webservices11.wsat.server;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContext;

import com.arjuna.webservices11.wsat.AtomicTransactionConstants;
import com.arjuna.webservices11.ServiceRegistry;
import com.arjuna.services.framework.startup.Sequencer;
import com.arjuna.wsc11.common.Environment;

/**
 * Activate the Completion Initiator service
 * @author kevin
 */
public class CompletionInitiatorInitialisation implements ServletContextListener
{
    /**
     * The context has been initialized.
     * @param servletContextEvent The servlet context event.
     */
    public void contextInitialized(final ServletContextEvent servletContextEvent)
    {

        Sequencer.Callback callback = new Sequencer.Callback(Sequencer.SEQUENCE_WSCOOR11, Sequencer.WEBAPP_WST11) {
           public void run() {
               final ServiceRegistry serviceRegistry = ServiceRegistry.getRegistry() ;
               String bindAddress = System.getProperty(Environment.XTS_BIND_ADDRESS);
               String bindPort = System.getProperty(Environment.XTS_BIND_PORT);
               String secureBindPort = System.getProperty(Environment.XTS_SECURE_BIND_PORT);

               if (bindAddress == null) {
                   bindAddress = "127.0.0.1";
               }

               if (bindPort == null) {
                   bindPort = "8080";
               }
               final String baseUri = "http://" +  bindAddress + ":" + bindPort + "/ws-t11/";
               final String uri = baseUri + AtomicTransactionConstants.COMPLETION_INITIATOR_SERVICE_NAME;

               serviceRegistry.registerServiceProvider(AtomicTransactionConstants.COMPLETION_INITIATOR_SERVICE_NAME, uri) ;
           }
        };
    }

    /**
     * The context is about to be destroyed.
     * @param servletContextEvent The servlet context event.
     */
    public void contextDestroyed(final ServletContextEvent servletContextEvent)
    {
    }
}