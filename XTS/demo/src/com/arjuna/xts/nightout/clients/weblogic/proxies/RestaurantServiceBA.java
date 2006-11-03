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
package com.arjuna.xts.nightout.clients.weblogic.proxies;

/**
 * Generated class, do not edit.
 *
 * This service interface was generated by weblogic
 * webservice stub gen on Wed Jul 21 12:42:33 BST 2004 */

public interface RestaurantServiceBA extends javax.xml.rpc.Service{

  weblogic.webservice.context.WebServiceContext context();

  weblogic.webservice.context.WebServiceContext joinContext() 
       throws weblogic.webservice.context.ContextNotFoundException;

  com.arjuna.xts.nightout.clients.weblogic.proxies.RestaurantServiceBAPort getRestaurantServiceBAPort() throws javax.xml.rpc.ServiceException;

  com.arjuna.xts.nightout.clients.weblogic.proxies.RestaurantServiceBAPort getRestaurantServiceBAPort(String username, String password) throws javax.xml.rpc.ServiceException;

}
