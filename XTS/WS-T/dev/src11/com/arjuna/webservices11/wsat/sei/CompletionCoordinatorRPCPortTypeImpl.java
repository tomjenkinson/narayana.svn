package com.arjuna.webservices11.wsat.sei;

import com.arjuna.webservices11.wsaddr.AddressingHelper;
import com.arjuna.webservices11.wsarj.ArjunaContext;
import com.arjuna.webservices11.wsat.processors.CompletionCoordinatorRPCProcessor;
import org.jboss.wsf.common.addressing.MAP;
import org.oasis_open.docs.ws_tx.wsat._2006._06.Notification;

import javax.annotation.Resource;
import javax.jws.*;
import javax.jws.soap.SOAPBinding;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.Addressing;

/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.1.1-b03-
 * Generated source version: 2.0
 *
 */
@WebService(name = "CompletionCoordinatorRPCPortType", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsat/2006/06",
        // wsdlLocation = "/WEB-INF/wsdl/wsat-completion-coordinator-binding.wsdl",
        serviceName = "CompletionCoordinatorRPCService",
        portName = "CompletionCoordinatorRPCPortType"
)
@SOAPBinding(parameterStyle = SOAPBinding.ParameterStyle.BARE)
@HandlerChain(file="/ws-t-rpc_handlers.xml")
@Addressing(required=true)
public class CompletionCoordinatorRPCPortTypeImpl // implements CompletionCoordinatorPortType
{
    @Resource
    private WebServiceContext webServiceCtx;

    /**
     *
     * @param parameters
     */
    @WebMethod(operationName = "CommitOperation", action = "http://docs.oasis-open.org/ws-tx/wsat/2006/06/Commit")
    @WebResult(name = "Result", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsat/2006/06", partName = "result")
    public boolean commitOperation(
        @WebParam(name = "Commit", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsat/2006/06", partName = "parameters")
        Notification parameters)
    {
        MessageContext ctx = webServiceCtx.getMessageContext();
        final Notification commit = parameters;
        final MAP inboundMap = AddressingHelper.inboundMap(ctx);
        final ArjunaContext arjunaContext = ArjunaContext.getCurrentContext(ctx);

        return CompletionCoordinatorRPCProcessor.getProcessor().commit(commit, inboundMap, arjunaContext) ;
    }

    /**
     *
     * @param parameters
     */
    @WebMethod(operationName = "RollbackOperation", action = "http://docs.oasis-open.org/ws-tx/wsat/2006/06/Rollback")
    @WebResult(name = "Result", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsat/2006/06", partName = "result")
    public boolean rollbackOperation(
        @WebParam(name = "Rollback", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsat/2006/06", partName = "parameters")
        Notification parameters)
    {
        MessageContext ctx = webServiceCtx.getMessageContext();
        final Notification rollback = parameters;
        final MAP inboundMap = AddressingHelper.inboundMap(ctx);
        final ArjunaContext arjunaContext = ArjunaContext.getCurrentContext(ctx);

        return CompletionCoordinatorRPCProcessor.getProcessor().rollback(rollback, inboundMap, arjunaContext) ;
    }
}
