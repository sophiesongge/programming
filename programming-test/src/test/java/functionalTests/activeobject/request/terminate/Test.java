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
package functionalTests.activeobject.request.terminate;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.BeforeClass;
import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.core.util.wrapper.StringWrapper;

import functionalTests.FunctionalTest;
import functionalTests.TestDisabler;
import functionalTests.activeobject.request.A;


/**
 * Test sending termination method
 */

public class Test extends FunctionalTest {
    A a1;

    A a2;

    StringWrapper returnedValue;

    @BeforeClass
    public static void disable() {
        TestDisabler.unstable();
    }

    @Before
    public void action() throws Exception {
        a1 = PAActiveObject.newActive(A.class, new Object[0]);
        a1.method1();
        a1.exit();

        // test with remaining ACs
        a2 = PAActiveObject.newActive(A.class, new Object[0]);
        a2.initDeleguate();
        returnedValue = a2.getDelegateValue();
        a2.exit();
    }

    @org.junit.Test
    public void postConditions() {
        assertTrue(returnedValue.getStringValue().equals("Returned value"));
        int exceptionCounter = 0;
        try {
            a1.method1();
        } catch (RuntimeException e) {
            exceptionCounter++;
        }
        try {
            a2.method1();
        } catch (RuntimeException e) {
            exceptionCounter++;
        }
        assertTrue(exceptionCounter == 2);
    }
}
