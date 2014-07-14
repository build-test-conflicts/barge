/**
 * Copyright 2013-2014 David Rusek <dave dot rusek at gmail dot com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.robotninjas.barge.jaxrs;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.sun.net.httpserver.HttpServer;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Level;


/**
 * A dedicated server for an instance of Raft using JDK's embedded HTTP server.
 */
public class RaftJdkServer implements RaftServer<RaftJdkServer> {

  private static final String help = "Usage: java -jar barge.jar [options] <server index>\n" +
      "Options:\n" +
      " -h                       : Displays this help message\n" +
      " -c <configuration file>  : Use given configuration file for cluster configuration\n" +
      "                            This file is a simple property file with indices as keys and URIs as values, eg. like\n\n" +
      "                              0=http://localhost:1234\n" +
      "                              1=http://localhost:3456\n" +
      "                              2=http://localhost:4567\n\n" +
      "                            Default is './barge.conf'\n" +
      "<server index>            : Index of this server in the cluster configuration\n";

  private final int serverIndex;
  private final URI[] uris;
  private final RaftApplication application;

  private HttpServer httpServer;

  public RaftJdkServer(int serverIndex, URI[] uris, File logDir) {
    this.serverIndex = serverIndex;
    this.uris = uris;
    this.application = new RaftApplication(serverIndex, uris, logDir);
  }

  public static void main(String[] args) throws IOException, URISyntaxException {
    muteJul();

    File clusterConfiguration = new File("barge.conf");
    int index = -1;

    for (int i = 0; i < args.length; i++) {

      switch (args[i]) {

        case "-c":
          clusterConfiguration = new File(args[++i]);

          break;

        case "-h":
          usage();
          System.exit(0);

        default:

          try {
            index = Integer.parseInt(args[i].trim());
          } catch (NumberFormatException e) {
            usage();
            System.exit(1);
          }

          break;
      }
    }

    if (index == -1) {
      usage();
      System.exit(1);
    }

    URI[] uris = readConfiguration(clusterConfiguration);

    RaftJdkServer server = new RaftJdkServer(index, uris, new File("log" + index)).start(0);

    waitForInput();

    server.stop();

    System.out.println("Bye!");
    System.exit(0);
  }

  private static void waitForInput() throws IOException {

    //noinspection ResultOfMethodCallIgnored
    System.in.read();
  }

  private static void muteJul() {
    java.util.logging.Logger.getLogger("").setLevel(Level.ALL);
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  private static URI[] readConfiguration(File clusterConfiguration) throws IOException, URISyntaxException {
    List<URI> uris = Lists.newArrayList();

    int lineNumber = 1;

    for (String line : CharStreams.readLines(new FileReader(clusterConfiguration))) {
      String[] pair = line.split("=");

      if (pair.length != 2)
        throw new IOException("Invalid cluster configuration at line " + lineNumber);

      uris.add(Integer.parseInt(pair[0].trim()), new URI(pair[1].trim()));
    }

    return uris.toArray(new URI[uris.size()]);
  }

  private static void usage() {
    System.out.println(help);
  }


  public RaftJdkServer start(int unusedPort) {
    this.httpServer = JdkHttpServerFactory.createHttpServer(UriBuilder.fromUri(uris[serverIndex]).path("raft").build(),
      application.makeResourceConfig());

    return this;
  }

  public void stop() {
    application.stop();
    httpServer.stop(0);
  }

  @Override
  public void clean() {

    try {
      application.clean();
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }


}
