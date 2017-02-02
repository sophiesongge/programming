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
package org.objectweb.proactive.core.body.exceptions;

import org.objectweb.proactive.core.ProActiveRuntimeException;


/**
 * Exception thrown by non active objects (e.g. main threads) when receiving a message.
 * @author The ProActive Team
 * @since 3.2.1
 */
public class HalfBodyException extends ProActiveRuntimeException {
    private static final String HALF_BODY_EXCEPTION_MESSAGE = "This method is not implemented in class HalfBody";

    public HalfBodyException() {
        super(HALF_BODY_EXCEPTION_MESSAGE);
    }
}
