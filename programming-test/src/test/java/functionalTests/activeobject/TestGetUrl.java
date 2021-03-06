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
package functionalTests.activeobject;

import java.io.IOException;

import org.junit.Test;
import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.ProActiveRuntimeException;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeException;

import functionalTests.GCMFunctionalTest;


public class TestGetUrl extends GCMFunctionalTest {

    public TestGetUrl() throws ProActiveException {
        super(1, 1);
        super.startDeployment();
    }

    @Test(expected = ProActiveRuntimeException.class)
    public void testNonAO() {
        Object o = new Object();
        String url = PAActiveObject.getUrl(o);
    }

    @Test
    public void testPAAactiveObject() throws ActiveObjectCreationException, NodeException, IOException {
        Node node = super.getANode();
        AO ao = PAActiveObject.newActive(AO.class, new Object[] {}, node);
        String url = PAActiveObject.getUrl(ao);
        ao = PAActiveObject.lookupActive(AO.class, url);
        ao.v();
    }

    public static class AO {
        public AO() {

        }

        public boolean v() {
            return true;
        }

    }

}
