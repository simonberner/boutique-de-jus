package io.bdj.web;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.bdj.Config;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.annotations.ServletContainerInitializersStarter;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.jaas.JAASLoginService;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.plus.jndi.EnvEntry;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.Configuration.ClassList;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.slf4j.Logger;

/**
 *
 */
public class WebServer {

  private static final Logger LOG = getLogger(WebServer.class);
  private final Config config;
  private Server server;
  private Path warfile;

  public WebServer(final Config config) {
    this.config = config;
  }

  private Server createServer() throws Exception {

    final String configFile = config.getJettyConfigFile();
    final Server server;
    if (configFile != null) {
      final URI configFileUri = Paths.get(configFile).toUri();
      LOG.info("Initializing server using config file {}", configFile);

      final Map<String, String> props = new HashMap<>();
      props.put("jetty.home", new File(System.getProperty("user.dir")).getCanonicalPath());

      server = loadServerFromConfigFile(configFileUri, props);

    } else {
      LOG.info("Initializing server using command line args");
      server = new Server(createThreadPool());
      attachServerConnector(server);

      ClassList classlist = ClassList.setServerDefault(server);
      classlist.addAfter("org.eclipse.jetty.webapp.FragmentConfiguration",
                         "org.eclipse.jetty.plus.webapp.EnvConfiguration",
                         "org.eclipse.jetty.plus.webapp.PlusConfiguration");
      classlist.addBefore("org.eclipse.jetty.webapp.JettyWebXmlConfiguration",
                          "org.eclipse.jetty.annotations.AnnotationConfiguration");

      System.setProperty("jetty.base", ".");

      final JAASLoginService loginService = prepareJaasLoginModule();
      server.addBean(loginService);
    }
    return server;
  }

  /**
   * Creates a web-app deployment for a war file gives as path
   *
   * @param webappWar
   *     path to the war file
   *
   * @return a webApplication context that can be deployed on the jetty server.
   */
  private static WebAppContext createWebApp(final Path webappWar) {

    final WebAppContext webapp = new WebAppContext();
    webapp.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
                        ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/[^/]*taglibs.*\\.jar$");

    /*
     * Configure the application to support the compilation of JSP files.
     * We need a new class loader and some stuff so that Jetty can call the
     * onStartup() methods as required.
     */
    webapp.setAttribute("org.eclipse.jetty.containerInitializers", jspInitializers());
    webapp.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
    webapp.addBean(new ServletContainerInitializersStarter(webapp), true);
    webapp.setContextPath("/");
    webapp.setWar(webappWar.toString());
    return webapp;
  }

  /**
   * Initializes the jetty server from XML configuratoin
   *
   * @param configFile
   *     jetty configuration file. The file must configure a server, otherwise the method will fail
   * @param props
   *  override properties
   *
   * @return the configured server instace
   *
   * @throws Exception
   */
  private static Server loadServerFromConfigFile(URI configFile, Map<String, String> props) throws Exception {

    XmlConfiguration configuration = new XmlConfiguration(configFile.toURL());
    configuration.getProperties().putAll(props);

    return (Server) configuration.configure();
  }

  /**
   * Creates a new thread pool for the server and hooks it's properties to the configuration events
   *
   * @return a new thread pool
   */
  private QueuedThreadPool createThreadPool() {

    final QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMinThreads(config.getHttpMinThreads());
    threadPool.setMaxThreads(config.getHttpMaxThreads());
    return threadPool;
  }

  /**
   * Attaches a server connector listening on the specified port to the server. Further the connector
   * is hooked into the configuration system to received runtime port changes
   *
   * @param server
   *     the Jetty server
   *
   */
  private void attachServerConnector(final Server server) {

    final ServerConnector connector = new ServerConnector(server);
    connector.setPort(config.getHttpPort());
    connector.setAcceptQueueSize(config.getHttpAcceptorQueueSize());
    server.addConnector(connector);
  }

  /**
   * Creeates a the JAAS login module and sets up the configuration
   *
   * @return the new jaas login module
   */
  private static JAASLoginService prepareJaasLoginModule() {

    final JAASLoginService loginService = new JAASLoginService();
    if (System.getProperty("java.security.auth.login.config") == null) {
      URL jaasConfigURL = WebServer.class.getResource("/login.conf");
      if (jaasConfigURL != null) {
        System.setProperty("java.security.auth.login.config", jaasConfigURL.toString());
      }
    }
    loginService.setName("BoutiqueDeJusRealm");
    loginService.setLoginModuleName("Boutique");
    return loginService;
  }

  /**
   * Initializer to activate JSP support in Jetty.
   *
   * @return list of initializer to support jsp compiling
   */
  private static List<ContainerInitializer> jspInitializers() {

    final JettyJasperInitializer sci = new JettyJasperInitializer();
    final ContainerInitializer initializer = new ContainerInitializer(sci, null);
    final List<ContainerInitializer> initializers = new ArrayList<>();
    initializers.add(initializer);
    return initializers;
  }

  /**
   * Sets the war file that should be deployed on the web container.
   * @param warfile
   *  Path to warfile that will be deployed
   * @return
   */
  public WebServer withWarfile(final Path warfile) {

    this.warfile = warfile;
    return this;
  }

  public void start() throws Exception {

    Objects.requireNonNull(warfile, "War file is not specificed");

    this.server = createServer();
    server.addBean(new EnvEntry("databaseUrl", config.getDatabaseUrl()));
    server.addBean(new EnvEntry("databaseDriver", config.getDatabaseDriver()));
    server.setHandler(createWebApp(warfile));
    server.start();
  }

  public void stop() throws Exception {

    this.server.stop();
  }

}
