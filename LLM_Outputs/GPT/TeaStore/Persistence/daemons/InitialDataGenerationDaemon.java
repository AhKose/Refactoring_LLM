/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.descartes.teastore.persistence.daemons;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.util.GlobalTracer;
import tools.descartes.teastore.persistence.repository.DataGenerator;
import tools.descartes.teastore.registryclient.RegistryClient;
import tools.descartes.teastore.registryclient.Service;
import tools.descartes.teastore.registryclient.tracing.Tracing;

/**
 * Application Lifecycle Listener implementation class for data generation.
 *
 * @author Joakim von Kistowski
 *
 */
@WebListener
public class InitialDataGenerationDaemon implements ServletContextListener {

  private static final Logger LOG = LoggerFactory.getLogger(InitialDataGenerationDaemon.class);

  private static final long DATABASE_OFFLINE_WAIT_MS = 2000;

  /**
   * Default constructor.
   */
  public InitialDataGenerationDaemon() {

  }

  /**
   * @see ServletContextListener#contextDestroyed(ServletContextEvent)
   * @param event
   *          The servlet context event at destruction.
   */
  public void contextDestroyed(ServletContextEvent event) {
    RegistryClient.getClient().unregister(event.getServletContext().getContextPath());
  }

  /**
   * @see ServletContextListener#contextInitialized(ServletContextEvent)
   * @param event
   *          The servlet context event at initialization.
   */
  @Override
  public void contextInitialized(ServletContextEvent event) {
    initTracing();
    waitForDatabase();
    generateInitialDataIfNeeded();
    LOG.info("Persistence finished initializing database");
    startRegistrationDaemon(event);
  }

  private void initTracing() {
    GlobalTracer.register(Tracing.init(Service.PERSISTENCE.getServiceName()));
  }

  private void generateInitialDataIfNeeded() {
    if (DataGenerator.GENERATOR.isDatabaseEmpty()) {
      DataGenerator.GENERATOR.generateDatabaseContent(DataGenerator.SMALL_DB_CATEGORIES,
          DataGenerator.SMALL_DB_PRODUCTS_PER_CATEGORY,
          DataGenerator.SMALL_DB_USERS,
          DataGenerator.SMALL_DB_MAX_ORDERS_PER_USER);
    }
    DataGenerator.GENERATOR.setMaintenanceModeGlobal(false);
  }

  private void startRegistrationDaemon(ServletContextEvent event) {
    RegistryClient.getClient().register(event.getServletContext().getContextPath());
    LOG.info("Persistence started registration daemon");
  }

  private void waitForDatabase() {
    while (true) {
      try {
        DataGenerator.GENERATOR.isDatabaseEmpty();
        return;
      } catch (PersistenceException e) {
        LOG.warn("Exception connecting to database. Is database offline? Waiting for "
            + DATABASE_OFFLINE_WAIT_MS + " ms.");
        if (!sleepQuietly(DATABASE_OFFLINE_WAIT_MS)) {
          return;
        }
      }
    }
  }

  private boolean sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Interrupted while waiting for database to come online.");
      return false;
    }
  }

}
