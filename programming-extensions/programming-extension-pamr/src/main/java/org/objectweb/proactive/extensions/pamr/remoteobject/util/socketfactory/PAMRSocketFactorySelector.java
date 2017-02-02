/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package org.objectweb.proactive.extensions.pamr.remoteobject.util.socketfactory;

import java.util.Iterator;

import javax.imageio.spi.ServiceRegistry;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.util.log.ProActiveLogger;
import org.objectweb.proactive.extensions.pamr.PAMRConfig;


/**
 * This class will instantiate the appropriate Socket Factory
 * according to user preferences
 *
 * @since ProActive 4.2.0
 */
public class PAMRSocketFactorySelector {

    static final Logger logger = ProActiveLogger.getLogger(PAMRConfig.Loggers.PAMR_CLIENT_TUNNEL);

    /**
     * aliases for the Socket Factories provided with ProActive
     */
    public static PAMRSocketFactorySPI get() {

        if (!PAMRConfig.PA_PAMR_SOCKET_FACTORY.isSet()) {
            // the user wants the default
            return getDefaultSocketFactory();
        }

        String socketFactory = PAMRConfig.PA_PAMR_SOCKET_FACTORY.getValue();

        Iterator<PAMRSocketFactorySPI> socketFactories = ServiceRegistry.lookupProviders(PAMRSocketFactorySPI.class);
        try {
            while (socketFactories.hasNext()) {
                PAMRSocketFactorySPI factory = socketFactories.next();
                if (socketFactory.equals(factory.getAlias()) || socketFactories.equals(factory.getClass().getName())) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Installing the " + factory.getAlias() + " (" + factory.getClass().getName() +
                                     ") socket factory for message routing");
                    }
                    return factory;
                }
            }
        } catch (Error e) {
            logger.warn("Failed to load a service provider for " + PAMRSocketFactorySPI.class.getName(), e);
        }

        logger.warn(socketFactory + " is neither an alias for a socket factory provided with ProActive,\n" +
                    "   nor a class name for a socket factory which could be found using the service provider mechanisms.\n" +
                    "   Will instantiate the default, plain socket factory.");

        return getDefaultSocketFactory();
    }

    private static PAMRSocketFactorySPI getDefaultSocketFactory() {
        return new PAMRPlainSocketFactory();
    }
}
