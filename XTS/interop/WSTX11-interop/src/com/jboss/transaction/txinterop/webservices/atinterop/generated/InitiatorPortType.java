/*
 * JBoss, Home of Professional Open Source
 * Copyright 2007, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package com.jboss.transaction.txinterop.webservices.atinterop.generated;

import javax.jws.Oneway;
import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.xml.ws.RequestWrapper;
import javax.xml.ws.soap.Addressing;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.1.2-hudson-182-RC1
 * Generated source version: 2.0
 * 
 */
@WebService(name = "InitiatorPortType", targetNamespace = "http://fabrikam123.com")
@Addressing(enabled=true, required=true)
public interface InitiatorPortType {


    /**
     * 
     */
    @WebMethod(operationName = "Response", action = "http://fabrikam123.com/Response")
    @Oneway
    @RequestWrapper(localName = "Response", targetNamespace = "http://fabrikam123.com", className = "com.jboss.transaction.txinterop.webservices.atinterop.generated.TestMessageType")
    public void response();

}
