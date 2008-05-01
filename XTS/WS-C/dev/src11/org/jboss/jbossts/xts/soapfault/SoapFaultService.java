
package org.jboss.jbossts.xts.soapfault;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.Service21;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.1.2-hudson-182-RC1
 * Generated source version: 2.0
 * 
 */
@WebServiceClient(name = "SoapFaultService", targetNamespace = "http://jbossts.jboss.org/xts/soapfault", wsdlLocation = "wsdl/soapfault.wsdl")
public class SoapFaultService
    extends Service21
{

    private final static URL SOAPFAULTSERVICE_WSDL_LOCATION;
    private final static Logger logger = Logger.getLogger(org.jboss.jbossts.xts.soapfault.SoapFaultService.class.getName());

    static {
        URL url = null;
        try {
            URL baseUrl;
            baseUrl = org.jboss.jbossts.xts.soapfault.SoapFaultService.class.getResource(".");
            url = new URL(baseUrl, "wsdl/soapfault.wsdl");
        } catch (MalformedURLException e) {
            logger.warning("Failed to create URL for the wsdl Location: 'wsdl/soapfault.wsdl', retrying as a local file");
            logger.warning(e.getMessage());
        }
        SOAPFAULTSERVICE_WSDL_LOCATION = url;
    }

    public SoapFaultService(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public SoapFaultService() {
        super(SOAPFAULTSERVICE_WSDL_LOCATION, new QName("http://jbossts.jboss.org/xts/soapfault", "SoapFaultService"));
    }

    /**
     * 
     * @return
     *     returns SoapFaultPortType
     */
    @WebEndpoint(name = "SoapFaultPortType")
    public SoapFaultPortType getSoapFaultPortType() {
        return super.getPort(new QName("http://jbossts.jboss.org/xts/soapfault", "SoapFaultPortType"), SoapFaultPortType.class);
    }

}
