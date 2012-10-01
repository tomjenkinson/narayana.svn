
package com.arjuna.webservices11.wsba.sei;

import com.arjuna.services.framework.task.Task;
import com.arjuna.services.framework.task.TaskManager;
import com.arjuna.webservices11.wsarj.ArjunaContext;
import com.arjuna.webservices11.wsba.processors.ParticipantCompletionParticipantProcessor;
import com.arjuna.webservices11.SoapFault11;
import org.jboss.ws.api.addressing.MAP;
import com.arjuna.webservices11.wsaddr.AddressingHelper;
import com.arjuna.webservices.SoapFault;
import org.oasis_open.docs.ws_tx.wsba._2006._06.NotificationType;
import org.oasis_open.docs.ws_tx.wsba._2006._06.StatusType;
import org.xmlsoap.schemas.soap.envelope.Fault;

import javax.annotation.Resource;
import javax.jws.*;
import javax.jws.soap.SOAPBinding;
import javax.xml.ws.Action;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.Addressing;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.1.1-b03-
 * Generated source version: 2.0
 *
 */
@WebService(name = "BusinessAgreementWithParticipantCompletionParticipantPortType", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsba/2006/06",
        //wsdlLocation = "/WEB-INF/wsdl/wsba-participant-completion-participant-binding.wsdl",
        serviceName = "BusinessAgreementWithParticipantCompletionParticipantService",
        portName = "BusinessAgreementWithParticipantCompletionParticipantPortType"
)
@SOAPBinding(parameterStyle = SOAPBinding.ParameterStyle.BARE)
@HandlerChain(file="/ws-t_handlers.xml")
@Addressing(required=true)
@org.apache.cxf.annotations.EndpointProperty(key = "soap.no.validate.parts", value = "true")
public class BusinessAgreementWithParticipantCompletionParticipantPortTypeImpl // implements BusinessAgreementWithParticipantCompletionParticipantPortType
{
    @Resource
    private WebServiceContext webServiceCtx;

    /**
     *
     * @param parameters
     */
    @WebMethod(operationName = "CloseOperation", action = "http://docs.oasis-open.org/ws-tx/wsba/2006/06/Close")
    @Oneway
    @Action(input="http://docs.oasis-open.org/ws-tx/wsba/2006/06/Close")
    public void closeOperation(
        @WebParam(name = "Close", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsba/2006/06", partName = "parameters")
        NotificationType parameters)
    {
        MessageContext ctx = webServiceCtx.getMessageContext();
        final NotificationType close = parameters;
        final MAP inboundMap = AddressingHelper.inboundMap(ctx);
        final ArjunaContext arjunaContext = ArjunaContext.getCurrentContext(ctx);

        TaskManager.getManager().queueTask(new Task() {
            public void executeTask() {
                ParticipantCompletionParticipantProcessor.getProcessor().close(close, inboundMap, arjunaContext) ;
            }
        }) ;
    }
    /**
     *
     * @param parameters
     */
    @WebMethod(operationName = "CancelOperation", action = "http://docs.oasis-open.org/ws-tx/wsba/2006/06/Cancel")
    @Oneway
    @Action(input="http://docs.oasis-open.org/ws-tx/wsba/2006/06/Cancel")
    public void cancelOperation(
        @WebParam(name = "Cancel", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsba/2006/06", partName = "parameters")
        NotificationType parameters)
    {
        MessageContext ctx = webServiceCtx.getMessageContext();
        final NotificationType cancel = parameters;
        final MAP inboundMap = AddressingHelper.inboundMap(ctx);
        final ArjunaContext arjunaContext = ArjunaContext.getCurrentContext(ctx);

        TaskManager.getManager().queueTask(new Task() {
            public void executeTask() {
                ParticipantCompletionParticipantProcessor.getProcessor().cancel(cancel, inboundMap, arjunaContext) ;
            }
        }) ;
    }

    /**
     *
     * @param parameters
     */
    @WebMethod(operationName = "CompensateOperation", action = "http://docs.oasis-open.org/ws-tx/wsba/2006/06/Compensate")
    @Oneway
    @Action(input="http://docs.oasis-open.org/ws-tx/wsba/2006/06/Compensate")
    public void compensateOperation(
        @WebParam(name = "Compensate", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsba/2006/06", partName = "parameters")
        NotificationType parameters)
    {
        MessageContext ctx = webServiceCtx.getMessageContext();
        final NotificationType compensate = parameters;
        final MAP inboundMap = AddressingHelper.inboundMap(ctx);
        final ArjunaContext arjunaContext = ArjunaContext.getCurrentContext(ctx);

        TaskManager.getManager().queueTask(new Task() {
            public void executeTask() {
                ParticipantCompletionParticipantProcessor.getProcessor().compensate(compensate, inboundMap, arjunaContext) ;
            }
        }) ;
    }

    /**
     *
     * @param parameters
     */
    @WebMethod(operationName = "FailedOperation", action = "http://docs.oasis-open.org/ws-tx/wsba/2006/06/Failed")
    @Oneway
    @Action(input="http://docs.oasis-open.org/ws-tx/wsba/2006/06/Failed")
    public void failedOperation(
        @WebParam(name = "Failed", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsba/2006/06", partName = "parameters")
        NotificationType parameters)
    {
        MessageContext ctx = webServiceCtx.getMessageContext();
        final NotificationType failed = parameters;
        final MAP inboundMap = AddressingHelper.inboundMap(ctx);
        final ArjunaContext arjunaContext = ArjunaContext.getCurrentContext(ctx);

        TaskManager.getManager().queueTask(new Task() {
            public void executeTask() {
                ParticipantCompletionParticipantProcessor.getProcessor().failed(failed, inboundMap, arjunaContext) ;
            }
        }) ;
    }

    /**
     *
     * @param parameters
     */
    @WebMethod(operationName = "ExitedOperation", action = "http://docs.oasis-open.org/ws-tx/wsba/2006/06/Exited")
    @Oneway
    @Action(input="http://docs.oasis-open.org/ws-tx/wsba/2006/06/Exited")
    public void exitedOperation(
        @WebParam(name = "Exited", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsba/2006/06", partName = "parameters")
        NotificationType parameters)
    {
        MessageContext ctx = webServiceCtx.getMessageContext();
        final NotificationType exited = parameters;
        final MAP inboundMap = AddressingHelper.inboundMap(ctx);
        final ArjunaContext arjunaContext = ArjunaContext.getCurrentContext(ctx);

        TaskManager.getManager().queueTask(new Task() {
            public void executeTask() {
                ParticipantCompletionParticipantProcessor.getProcessor().exited(exited, inboundMap, arjunaContext) ;
            }
        }) ;
    }

    /**
     *
     * @param parameters
     */
    @WebMethod(operationName = "NotCompleted", action = "http://docs.oasis-open.org/ws-tx/wsba/2006/06/NotCompleted")
    @Oneway
    @Action(input="http://docs.oasis-open.org/ws-tx/wsba/2006/06/NotCompleted")
    public void notCompleted(
        @WebParam(name = "NotCompleted", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsba/2006/06", partName = "parameters")
        NotificationType parameters)
    {
        MessageContext ctx = webServiceCtx.getMessageContext();
        final NotificationType notCompleted = parameters;
        final MAP inboundMap = AddressingHelper.inboundMap(ctx);
        final ArjunaContext arjunaContext = ArjunaContext.getCurrentContext(ctx);

        TaskManager.getManager().queueTask(new Task() {
            public void executeTask() {
                ParticipantCompletionParticipantProcessor.getProcessor().notCompleted(notCompleted, inboundMap, arjunaContext) ;
            }
        }) ;
    }

    /**
     *
     * @param parameters
     */
    @WebMethod(operationName = "GetStatusOperation", action = "http://docs.oasis-open.org/ws-tx/wsba/2006/06/GetStatus")
    @Oneway
    @Action(input="http://docs.oasis-open.org/ws-tx/wsba/2006/06/GetStatus")
    public void getStatusOperation(
        @WebParam(name = "GetStatus", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsba/2006/06", partName = "parameters")
        NotificationType parameters)
    {
        MessageContext ctx = webServiceCtx.getMessageContext();
        final NotificationType getStatus = parameters;
        final MAP inboundMap = AddressingHelper.inboundMap(ctx);
        final ArjunaContext arjunaContext = ArjunaContext.getCurrentContext(ctx);

        TaskManager.getManager().queueTask(new Task() {
            public void executeTask() {
                ParticipantCompletionParticipantProcessor.getProcessor().getStatus(getStatus, inboundMap, arjunaContext) ;
            }
        }) ;
    }

    /**
     *
     * @param parameters
     */
    @WebMethod(operationName = "StatusOperation", action = "http://docs.oasis-open.org/ws-tx/wsba/2006/06/Status")
    @Oneway
    @Action(input="http://docs.oasis-open.org/ws-tx/wsba/2006/06/Status")
    public void statusOperation(
        @WebParam(name = "Status", targetNamespace = "http://docs.oasis-open.org/ws-tx/wsba/2006/06", partName = "parameters")
        StatusType parameters)
    {
        MessageContext ctx = webServiceCtx.getMessageContext();
        final StatusType status = parameters;
        final MAP inboundMap = AddressingHelper.inboundMap(ctx);
        final ArjunaContext arjunaContext = ArjunaContext.getCurrentContext(ctx);

        TaskManager.getManager().queueTask(new Task() {
            public void executeTask() {
                ParticipantCompletionParticipantProcessor.getProcessor().status(status, inboundMap, arjunaContext) ;
            }
        }) ;
    }

    @WebMethod(operationName = "fault", action = "http://docs.oasis-open.org/ws-tx/wscoor/2006/06/fault")
    @Oneway
    @Action(input="http://docs.oasis-open.org/ws-tx/wscoor/2006/06/fault")
    public void soapFault(
            @WebParam(name = "Fault", targetNamespace = "http://schemas.xmlsoap.org/soap/envelope/", partName = "parameters")
            Fault fault)
    {
        MessageContext ctx = webServiceCtx.getMessageContext();
        final MAP inboundMap = AddressingHelper.inboundMap(ctx);
        final ArjunaContext arjunaContext = ArjunaContext.getCurrentContext(ctx);
        final SoapFault soapFault = SoapFault11.fromFault(fault);

        TaskManager.getManager().queueTask(new Task() {
            public void executeTask() {
                ParticipantCompletionParticipantProcessor.getProcessor().soapFault(soapFault, inboundMap, arjunaContext); ;
            }
        }) ;
    }
}