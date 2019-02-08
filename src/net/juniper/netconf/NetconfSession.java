/**
 * Copyright (c) 2013 Juniper Networks, Inc.
 * All Rights Reserved
 * <p>
 * Use is subject to license terms.
 */

package net.juniper.netconf;

import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import net.juniper.netconf.exception.CommitException;
import net.juniper.netconf.exception.LoadException;
import net.juniper.netconf.exception.NetconfException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * A <code>NetconfSession</code> object is used to call the Netconf driver
 * methods.
 * This is derived by creating a Device first,
 * and calling createNetconfSession().
 * <p>
 * Typically, one
 * <ol>
 * <li>creates a Device object.</li>
 * <li>calls the createNetconfSession() method to get a NetconfSession
 * object.</li>
 * <li>perform operations on the NetconfSession object.</li>
 * <li>finally, one must close the NetconfSession and release resources with
 * the {@link #close() close()} method.</li>
 * </ol>
 */
public class NetconfSession {

    private final static Logger LOG = LoggerFactory.getLogger(NetconfSession.class);

    public final static String PROMPT = "]]>]]>";

    private final static String RPC_OPEN = "<rpc>";
    private final static String RPC_CLOSE = "</rpc>";
    private final static String TAG_OPEN = "<";
    private final static String TAG_CLOSE = "/>";

    private final static int RESPONSE_TIMEOUT = 200000;
    private final static int THREAD_TIMEOUT = 300;


    private Session netconfSession;
    private String serverCapability;
    private InputStream stdout;
    private BufferedReader bufferReader;
    private String lastRpcReply;
    private DocumentBuilder builder;

    protected NetconfSession(Session netconfSession, String hello,
                             DocumentBuilder builder) throws NetconfException, IOException {
        this.netconfSession = netconfSession;
        this.builder = builder;
        stdout = new StreamGobbler(netconfSession.getStdout());
        bufferReader = new BufferedReader(new InputStreamReader(stdout));
        sendHello(hello);
    }

    private XML convertToXML(String xml) throws SAXException, IOException {
        LOG.debug("Input xml [{}]", xml);
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        Element root = doc.getDocumentElement();
        return new XML(root);
    }

    private void sendHello(String hello) throws IOException {
        String reply = getRpcReply(hello);
        serverCapability = reply;
        lastRpcReply = reply;
    }

    private String getRpcReply(String rpc) throws IOException {
        LOG.info("Running Command: {}", rpc);
        netconfSession.getStdin().write(rpc.getBytes());
        return getResponseString(PROMPT, stdout);
    }


    private BufferedReader getRpcReplyRunning(String rpc) throws IOException {
        byte b[] = rpc.getBytes();
        netconfSession.getStdin().write(b);
        return bufferReader;
    }

    private void loadXMLConfiguration(String target, String configuration,
                                      String loadType) throws LoadException, IOException, SAXException {

        configuration = configuration.trim();
        if (!configuration.startsWith("<configuration")) {
            configuration = "<configuration>" + configuration
                    + "</configuration>";
        }
        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN)
                .append("<edit-config>")
                .append("<target>")
                .append(TAG_OPEN)
                .append(target)
                .append(TAG_CLOSE)
                .append("</target>")
                .append("<default-operation>")
                .append(loadType)
                .append("</default-operation>")
                .append("<config>")
                .append(configuration)
                .append("</config>")
                .append("</edit-config>")
                .append(RPC_CLOSE)
                .append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
        if (hasError() || !isOK())
            throw new LoadException("Load operation returned error.");
    }

    private void loadTextConfiguration(String target, String configuration,
                                       String loadType) throws LoadException, IOException, SAXException {
        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN)
                .append("<edit-config>")
                .append("<target>")
                .append(TAG_OPEN)
                .append(target)
                .append(TAG_CLOSE)
                .append("</target>")
                .append("<default-operation>")
                .append(loadType)
                .append("</default-operation>")
                .append("<config-text>")
                .append("<configuration-text>")
                .append(configuration)
                .append("</configuration-text>")
                .append("</config-text>")
                .append("</edit-config>")
                .append(RPC_CLOSE)
                .append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
        if (hasError() || !isOK())
            throw new LoadException("Load operation returned error");
    }

    private String getConfig(String target, String configTree)
            throws IOException {

        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN);
        rpc.append("<get-config>");
        rpc.append("<source>");
        rpc.append(TAG_OPEN + target + TAG_CLOSE);
        rpc.append("</source>");
        rpc.append("<filter type=\"subtree\">");
        rpc.append(configTree);
        rpc.append("</filter>");
        rpc.append("</get-config>");
        rpc.append(RPC_CLOSE);
        rpc.append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
        return lastRpcReply;
    }

    private String readFile(String path) throws FileNotFoundException, IOException {
        File file = new File(path);
        FileInputStream fin = new FileInputStream(file);
        byte fileContent[] = new byte[(int) file.length()];
        fin.read(fileContent);
        return new String(fileContent);
    }

    /**
     * Get capability of the Netconf server.
     *
     * @return server capability
     */
    public String getServerCapability() {
        return serverCapability;
    }

    /**
     * Send an RPC(as String object) over the default Netconf session and get
     * the response as an XML object.
     * <p>
     *
     * @param rpcContent RPC content to be sent. For example, to send an rpc
     *                   &lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;, the
     *                   String to be passed can be
     *                   "&lt;get-chassis-inventory/&gt;" OR
     *                   "get-chassis-inventory" OR
     *                   "&lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;"
     * @return RPC reply sent by Netconf server
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML executeRPC(String rpcContent) throws SAXException, IOException {
        if (rpcContent == null) {
            throw new IllegalArgumentException("RpcContent can't be null");
        }
        rpcContent = rpcContent.trim();
        if (!rpcContent.startsWith(RPC_OPEN) && !rpcContent.equals("<rpc/>")) {
            if (rpcContent.startsWith(TAG_OPEN))
                rpcContent = RPC_OPEN + rpcContent + RPC_CLOSE;
            else
                rpcContent = RPC_OPEN + TAG_OPEN + rpcContent + TAG_CLOSE + RPC_CLOSE;
        }
        rpcContent += PROMPT;
        String rpcReply = getRpcReply(rpcContent);
        lastRpcReply = rpcReply;
        return convertToXML(rpcReply);
    }

    /**
     * Send an RPC(as XML object) over the Netconf session and get the response
     * as an XML object.
     * <p>
     *
     * @param rpc RPC to be sent. Use the XMLBuilder to create RPC as an
     *            XML object.
     * @return RPC reply sent by Netconf server
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML executeRPC(XML rpc) throws SAXException, IOException {
        return executeRPC(rpc.toString());
    }

    /**
     * Send an RPC(as Document object) over the Netconf session and get the
     * response as an XML object.
     * <p>
     *
     * @param rpcDoc RPC content to be sent, as a org.w3c.dom.Document object.
     * @return RPC reply sent by Netconf server
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML executeRPC(Document rpcDoc) throws SAXException, IOException {
        Element root = rpcDoc.getDocumentElement();
        XML xml = new XML(root);
        return executeRPC(xml);
    }

    /**
     * Send an RPC(as String object) over the default Netconf session and get
     * the response as a BufferedReader.
     * <p>
     *
     * @param rpcContent RPC content to be sent. For example, to send an rpc
     *                   &lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;, the
     *                   String to be passed can be
     *                   "&lt;get-chassis-inventory/&gt;" OR
     *                   "get-chassis-inventory" OR
     *                   "&lt;rpc&gt;&lt;get-chassis-inventory/&gt;&lt;/rpc&gt;"
     * @return RPC reply sent by Netconf server as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till command execution completes.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public BufferedReader executeRPCRunning(String rpcContent)
            throws IOException, SAXException {
        if (rpcContent == null) {
            throw new IllegalArgumentException("RpcContent can't be null");
        }
        rpcContent = rpcContent.trim();
        if (!rpcContent.startsWith(RPC_OPEN) && !rpcContent.equals("<rpc/>")) {
            if (rpcContent.startsWith(TAG_OPEN))
                rpcContent = RPC_OPEN + rpcContent + RPC_CLOSE;
            else
                rpcContent = RPC_OPEN + TAG_OPEN + rpcContent + TAG_CLOSE + RPC_CLOSE;
        }
        rpcContent += PROMPT;
        return getRpcReplyRunning(rpcContent);
    }

    /**
     * Send an RPC(as XML object) over the Netconf session and get the response
     * as a BufferedReader.
     * <p>
     *
     * @param rpc RPC to be sent. Use the XMLBuilder to create RPC as an
     *            XML object.
     * @return RPC reply sent by Netconf server as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till command execution completes.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public BufferedReader executeRPCRunning(XML rpc) throws IOException,
            SAXException {
        return executeRPCRunning(rpc.toString());
    }

    /**
     * Send an RPC(as Document object) over the Netconf session and get the
     * response as a BufferedReader.
     * <p>
     *
     * @param rpcDoc RPC content to be sent, as a org.w3c.dom.Document object.
     * @return RPC reply sent by Netconf server as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till command execution completes.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public BufferedReader executeRPCRunning(Document rpcDoc) throws IOException,
            SAXException {
        Element root = rpcDoc.getDocumentElement();
        XML xml = new XML(root);
        return executeRPCRunning(xml);
    }

    /**
     * Get the session ID of the Netconf session.
     *
     * @return Session ID as a string.
     */
    public String getSessionId() {
        String split[] = serverCapability.split("<session-id>");
        if (split.length != 2)
            return null;
        String idSplit[] = split[1].split("</session-id>");
        if (idSplit.length != 2)
            return null;
        return idSplit[0];
    }

    /**
     * Close the Netconf session. You should always call this once you don't
     * need the session anymore.
     */
    public void close() throws IOException {
        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN);
        rpc.append("<close-session/>");
        rpc.append(RPC_CLOSE);
        rpc.append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
        netconfSession.close();
    }

    /**
     * Check if the last RPC reply returned from Netconf server has any error.
     *
     * @return true if any errors are found in last RPC reply.
     */
    public boolean hasError() throws SAXException, IOException {
        if (lastRpcReply == null || !(lastRpcReply.indexOf("<rpc-error>") >= 0))
            return false;
        XML xmlReply = convertToXML(lastRpcReply);
        List tagList = new ArrayList();
        tagList.add("rpc-error");
        tagList.add("error-severity");
        String errorSeverity = xmlReply.findValue(tagList);
        if (errorSeverity != null && errorSeverity.equals("error"))
            return true;
        return false;
    }

    /**
     * Check if the last RPC reply returned from Netconf server has any warning.
     *
     * @return true if any errors are found in last RPC reply.
     */
    public boolean hasWarning() throws SAXException, IOException {
        if (lastRpcReply == null || !(lastRpcReply.indexOf("<rpc-error>") >= 0))
            return false;
        XML xmlReply = convertToXML(lastRpcReply);
        List tagList = new ArrayList();
        tagList.add("rpc-error");
        tagList.add("error-severity");
        String errorSeverity = xmlReply.findValue(tagList);
        if (errorSeverity != null && errorSeverity.equals("warning"))
            return true;
        return false;
    }

    /**
     * Check if the last RPC reply returned from Netconf server,
     * contains &lt;ok/&gt; tag.
     *
     * @return true if &lt;ok/&gt; tag is found in last RPC reply.
     */
    public boolean isOK() {
        if (lastRpcReply != null && lastRpcReply.indexOf("<ok/>") >= 0)
            return true;
        return false;
    }

    /**
     * Locks the candidate configuration.
     *
     * @return true if successful.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public boolean lockConfig() throws IOException, SAXException {
        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN);
        rpc.append("<lock>");
        rpc.append("<target>");
        rpc.append("<candidate/>");
        rpc.append("</target>");
        rpc.append("</lock>");
        rpc.append(RPC_CLOSE);
        rpc.append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
        if (hasError() || !isOK())
            return false;
        return true;
    }

    /**
     * Unlocks the candidate configuration.
     *
     * @return true if successful.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public boolean unlockConfig() throws IOException, SAXException {
        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN);
        rpc.append("<unlock>");
        rpc.append("<target>");
        rpc.append("<candidate/>");
        rpc.append("</target>");
        rpc.append("</unlock>");
        rpc.append(RPC_CLOSE);
        rpc.append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
        if (hasError() || !isOK())
            return false;
        return true;
    }

    /**
     * Loads the candidate configuration, Configuration should be in XML format.
     *
     * @param configuration Configuration,in XML format, to be loaded. For example,
     *                      "&lt;configuration&gt;&lt;system&gt;&lt;services&gt;&lt;ftp/&gt;&lt;
     *                      services/&gt;&lt;/system&gt;&lt;/configuration/&gt;"
     *                      will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType      You can choose "merge" or "replace" as the loadType.
     * @throws LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadXMLConfiguration(String configuration, String loadType)
            throws LoadException, IOException, SAXException {
        if (!"merge".equalsIgnoreCase(loadType) && !"replace".equalsIgnoreCase(loadType)) {
            throw new IllegalArgumentException("'loadType' argument must be " +
                    "merge|replace");
        }

        loadXMLConfiguration("candidate", configuration, loadType);
    }

    /**
     * Loads the candidate configuration, Configuration should be in text/tree
     * format.
     *
     * @param configuration Configuration,in text/tree format, to be loaded. For example,
     *                      " system {
     *                      services {
     *                      ftp;
     *                      }
     *                      }"
     *                      will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType      You can choose "merge" or "replace" as the loadType.
     * @throws LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadTextConfiguration(String configuration, String loadType)
            throws LoadException, IOException, SAXException {
        if (!"merge".equalsIgnoreCase(loadType) && !"replace".equalsIgnoreCase(loadType)) {
            throw new IllegalArgumentException("'loadType' argument must be " +
                    "merge|replace");
        }

        loadTextConfiguration("candidate", configuration, loadType);
    }

    /**
     * Loads the candidate configuration, Configuration should be in set
     * format.
     * NOTE: This method is applicable only for JUNOS release 11.4 and above.
     *
     * @param configuration Configuration,in set format, to be loaded. For example,
     *                      "set system services ftp"
     *                      will load 'ftp' under the 'systems services' hierarchy.
     *                      To load multiple set statements, separate them by '\n' character.
     * @throws LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadSetConfiguration(String configuration) throws LoadException,
            IOException,
            SAXException {
        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN);
        rpc.append("<load-configuration action=\"set\">");
        rpc.append("<configuration-set>");
        rpc.append(configuration);
        rpc.append("</configuration-set>");
        rpc.append("</load-configuration>");
        rpc.append(RPC_CLOSE);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
        if (hasError() || !isOK())
            throw new LoadException("Load operation returned error");
    }

    /**
     * Loads the candidate configuration from file,
     * configuration should be in XML format.
     *
     * @param configFile Path name of file containing configuration,in xml format,
     *                   to be loaded.
     * @param loadType   You can choose "merge" or "replace" as the loadType.
     * @throws LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadXMLFile(String configFile, String loadType)
            throws LoadException, IOException, SAXException {
        String configuration = "";
        try {
            configuration = readFile(configFile);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("The system cannot find the " +
                    "configuration file specified: " + configFile);
        }
        if (loadType == null || (!loadType.equals("merge") &&
                !loadType.equals("replace")))
            throw new IllegalArgumentException("'loadType' argument must be " +
                    "merge|replace");
        loadXMLConfiguration(configuration, loadType);
    }

    /**
     * Loads the candidate configuration from file,
     * configuration should be in text/tree format.
     *
     * @param configFile Path name of file containing configuration,in xml format,
     *                   to be loaded.
     * @param loadType   You can choose "merge" or "replace" as the loadType.
     * @throws LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadTextFile(String configFile, String loadType)
            throws LoadException, IOException, SAXException {
        String configuration = "";
        try {
            configuration = readFile(configFile);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("The system cannot find the " +
                    "configuration file specified: " + configFile);
        }
        if (loadType == null || (!loadType.equals("merge") &&
                !loadType.equals("replace")))
            throw new IllegalArgumentException("'loadType' argument must be " +
                    "merge|replace");
        loadTextConfiguration(configuration, loadType);
    }

    /**
     * Loads the candidate configuration from file,
     * configuration should be in set format.
     * NOTE: This method is applicable only for JUNOS release 11.4 and above.
     *
     * @param configFile Path name of file containing configuration,in set format,
     *                   to be loaded.
     * @throws LoadException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void loadSetFile(String configFile) throws
            IOException, LoadException, SAXException {
        String configuration = "";
        try {
            configuration = readFile(configFile);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("The system cannot find the " +
                    "configuration file specified: " + configFile);
        }
        loadSetConfiguration(configuration);
    }

    /**
     * Loads and commits the candidate configuration, Configuration can be in
     * text/xml/set format.
     *
     * @param configFile Path name of file containing configuration,in text/xml/set format,
     *                   to be loaded. For example,
     *                   " system {
     *                   services {
     *                   ftp;
     *                   }
     *                   }"
     *                   will load 'ftp' under the 'systems services' hierarchy.
     *                   OR
     *                   "&lt;configuration&gt;&lt;system&gt;&lt;services&gt;&lt;ftp/&gt;&lt;
     *                   services/&gt;&lt;/system&gt;&lt;/configuration/&gt;"
     *                   will load 'ftp' under the 'systems services' hierarchy.
     *                   OR
     *                   "set system services ftp"
     *                   will load 'ftp' under the 'systems services' hierarchy.
     * @param loadType   You can choose "merge" or "replace" as the loadType.
     *                   NOTE: This parameter's value is redundant in case the file contains
     *                   configuration in 'set' format.
     * @throws LoadException
     * @throws CommitException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void commitThisConfiguration(String configFile, String loadType)
            throws LoadException, CommitException, IOException, SAXException {
        String configuration = "";
        try {
            configuration = readFile(configFile);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("The system cannot find the " +
                    "configuration file specified: " + configFile);
        }
        configuration = configuration.trim();
        if (this.lockConfig()) {
            if (configuration.startsWith(TAG_OPEN)) {
                this.loadXMLConfiguration(configuration, loadType);
            } else if (configuration.startsWith("set")) {
                this.loadSetConfiguration(configuration);
            } else {
                this.loadTextConfiguration(configuration, loadType);
            }
            this.commit();
        } else {
            throw new IOException("Unclean lock operation. Cannot proceed " +
                    "further.");
        }
        this.unlockConfig();
    }

    /**
     * Commit the candidate configuration.
     *
     * @throws CommitException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void commit() throws CommitException, IOException, SAXException {
        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN);
        rpc.append("<commit/>");
        rpc.append(RPC_CLOSE);
        rpc.append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
        if (hasError() || !isOK())
            throw new CommitException("Commit operation returned error.");
    }

    /**
     * Commit the candidate configuration, temporarily. This is equivalent of
     * 'commit confirm'
     *
     * @param seconds Time in seconds, after which the previous active configuration
     *                is reverted back to.
     * @throws CommitException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public void commitConfirm(long seconds) throws CommitException, IOException,
            SAXException {
        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN)
                .append("<commit>")
                .append("<confirmed/>")
                .append("<confirm-timeout>")
                .append(seconds)
                .append("</confirm-timeout>")
                .append("</commit>")
                .append(RPC_CLOSE)
                .append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
        if (hasError() || !isOK())
            throw new CommitException("Commit operation returned " +
                    "error.");
    }

    /**
     * Retrieve the candidate configuration, or part of the configuration.
     *
     * @param configTree configuration hierarchy to be retrieved as the argument.
     *                   For example, to get the whole configuration, argument should be
     *                   &lt;configuration&gt;&lt;/configuration&gt;
     * @return configuration data as XML object.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML getCandidateConfig(String configTree) throws SAXException,
            IOException {
        return convertToXML(getConfig("candidate", configTree));
    }

    /**
     * Retrieve the running configuration, or part of the configuration.
     *
     * @param configTree configuration hierarchy to be retrieved as the argument.
     *                   For example, to get the whole configuration, argument should be
     *                   &lt;configuration&gt;&lt;/configuration&gt;
     * @return configuration data as XML object.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML getRunningConfig(String configTree) throws SAXException,
            IOException {
        return convertToXML(getConfig("running", configTree));
    }

    /**
     * Retrieve the whole candidate configuration.
     *
     * @return configuration data as XML object.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML getCandidateConfig() throws SAXException, IOException {
        return convertToXML(getConfig("candidate",
                "<configuration></configuration>"));
    }

    /**
     * Retrieve the whole running configuration.
     *
     * @return configuration data as XML object.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public XML getRunningConfig() throws SAXException, IOException {
        return convertToXML(getConfig("running",
                "<configuration></configuration>"));
    }

    /**
     * Validate the candidate configuration.
     *
     * @return true if validation successful.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public boolean validate() throws IOException, SAXException {

        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN)
                .append("<validate>")
                .append("<source>")
                .append("<candidate/>")
                .append("</source>")
                .append("</validate>")
                .append(RPC_CLOSE)
                .append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
        if (hasError() || !isOK())
            return false;
        return true;
    }

    /**
     * Reboot the device corresponding to the Netconf Session.
     *
     * @return RPC reply sent by Netconf server.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public String reboot() throws SAXException, IOException {
        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN);
        rpc.append("<request-reboot/>");
        rpc.append(RPC_CLOSE);
        rpc.append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        return rpcReply;
    }

    /**
     * Run a cli command.
     * NOTE: The text output is supported for JUNOS 11.4 and later.
     *
     * @param command the cli command to be executed.
     * @return result of the command, as a String.
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    public String runCliCommand(String command) throws IOException, SAXException {

        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN);
        rpc.append("<command format=\"text\">");
        rpc.append(command);
        rpc.append("</command>");
        rpc.append(RPC_CLOSE);
        rpc.append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
        XML xmlReply = convertToXML(rpcReply);
        ArrayList tags = new ArrayList();
        tags.add("output");
        String output = xmlReply.findValue(tags);
        if (output != null)
            return output;
        else
            return rpcReply;
    }

    /**
     * Run a cli command.
     *
     * @param command the cli command to be executed.
     * @return result of the command, as a BufferedReader. This is
     * useful if we want continuous stream of output, rather than wait
     * for whole output till command execution completes.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public BufferedReader runCliCommandRunning(String command) throws
            SAXException, IOException {

        StringBuilder rpc = new StringBuilder();
        rpc.append("<command format=\"text\">");
        rpc.append(command);
        rpc.append("</command>");
        BufferedReader br = executeRPCRunning(rpc.toString());
        return br;
    }

    /**
     * This method should be called for load operations to happen in 'private'
     * mode.
     *
     * @param mode Mode in which to open the configuration.
     *             Permissible mode(s): "private"
     * @throws java.io.IOException
     */
    public void openConfiguration(String mode) throws IOException {

        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN);
        rpc.append("<open-configuration>");
        if (mode.startsWith(TAG_OPEN)) {
            rpc.append(mode);
        } else {
            rpc.append(TAG_OPEN)
                    .append(mode)
                    .append(TAG_CLOSE);
        }

        rpc.append("</open-configuration>")
                .append(RPC_CLOSE)
                .append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
    }

    /**
     * This method should be called to close a private session, in case its
     * started.
     *
     * @throws java.io.IOException
     */
    public void closeConfiguration() throws IOException {
        StringBuilder rpc = new StringBuilder();
        rpc.append(RPC_OPEN);
        rpc.append("<close-configuration/>");
        rpc.append(RPC_CLOSE);
        rpc.append(PROMPT);
        String rpcReply = getRpcReply(rpc.toString());
        lastRpcReply = rpcReply;
    }

    /**
     * Returns the last RPC reply sent by Netconf server.
     *
     * @return Last RPC reply, as a string.
     */
    public String getLastRPCReply() {
        return this.lastRpcReply;
    }


    private String getResponseString(String end, InputStream is) throws IOException {
        long startTime = System.currentTimeMillis();
        StringBuilder result = new StringBuilder();

        byte buffer[] = new byte[64];
        while (result.indexOf(end) < 0) {
            if ((System.currentTimeMillis() - startTime) > RESPONSE_TIMEOUT) {
                LOG.warn("Response timeout exceeded");
                throw new IOException("Response timeout exceeded");
            }
            if (is.available() > 0) {
                int byteRead = is.read(buffer);
                result.append(new String(buffer, 0, byteRead));
            } else {
                try {
                    Thread.sleep(THREAD_TIMEOUT);
                } catch (InterruptedException ex) {
                    LOG.error("InterruptedException ex=", ex);
                }
            }
        }
        LOG.info("Command Response extraction complete.");
        LOG.debug("Get response string [{}]", result.toString());

        int linePosition = result.indexOf(end);
        // Remove end line from result
        if (linePosition >= 0) {
            result.setLength(linePosition);
            LOG.info("Cut responese to length: {}", linePosition);
        } else {
            LOG.warn("Incorrect responese length: {}", linePosition);
        }

        return result.toString();
    }
}
