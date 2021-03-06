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
package org.objectweb.proactive.core.descriptor.legacyparser;

import org.objectweb.proactive.core.descriptor.data.ProActiveDescriptorInternal;
import org.objectweb.proactive.core.process.filetransfer.FileTransferDefinition;
import org.objectweb.proactive.core.xml.handler.BasicUnmarshaller;
import org.objectweb.proactive.core.xml.handler.PassiveCompositeUnmarshaller;
import org.objectweb.proactive.core.xml.handler.UnmarshallerHandler;
import org.objectweb.proactive.core.xml.io.Attributes;
import org.xml.sax.SAXException;


/**
 * @author The ProActive Team
 *
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public class FileTransferDefinitionsHandler extends PassiveCompositeUnmarshaller
        implements ProActiveDescriptorConstants {
    protected ProActiveDescriptorInternal proActiveDescriptor;

    public FileTransferDefinitionsHandler(ProActiveDescriptorInternal proActiveDescriptor) {
        super(false);
        this.proActiveDescriptor = proActiveDescriptor;
        addHandler(FILE_TRANSFER_TAG, new FileTransferHandler());
    }

    @Override
    protected void notifyEndActiveHandler(String name, UnmarshallerHandler activeHandler) throws SAXException {
    }

    public class FileTransferHandler extends PassiveCompositeUnmarshaller implements ProActiveDescriptorConstants {
        protected FileTransferDefinition fileTransfer;

        public FileTransferHandler() {
            super(false);
            addHandler(FILE_TRANSFER_FILE_TAG, new FileHandler());
            addHandler(FILE_TRANSFER_DIR_TAG, new DirHandler());

            //This will be initialized once we have the ID in in the startContextElement(...)
            fileTransfer = null;
        }

        @Override
        public void startContextElement(String name, Attributes attributes) throws SAXException {
            String fileTransferId = attributes.getValue("id");

            if (!checkNonEmpty(fileTransferId)) {
                throw new org.xml.sax.SAXException("FileTransfer defined without id");
            }

            if (fileTransferId.equalsIgnoreCase(FILE_TRANSFER_IMPLICT_KEYWORD)) {
                throw new org.xml.sax.SAXException("FileTransferDefinition id attribute is using illegal keyword: " +
                                                   FILE_TRANSFER_IMPLICT_KEYWORD);
            }

            /*
             * We get a reference on the FileTransfer object with this ID.
             * If this object doesn't exist the createFileTransfer will create
             * one. All future calls on this method will then return this same
             * instance for this ID.
             */
            fileTransfer = proActiveDescriptor.getFileTransfer(fileTransferId);
        }

        @Override
        public Object getResultObject() throws SAXException {
            return fileTransfer; //not really used for now
        }

        public class FileHandler extends BasicUnmarshaller implements ProActiveDescriptorConstants {
            @Override
            public void startContextElement(String name, Attributes attributes) throws SAXException {
                String source = attributes.getValue("src");
                String dest = attributes.getValue("dest");

                if (!checkNonEmpty(source)) {
                    throw new org.xml.sax.SAXException("Source filename not specified for file tag");
                }

                if (!checkNonEmpty(dest)) {
                    dest = source;
                }

                //fileTransfer variable is in the parent class
                fileTransfer.addFile(source, dest);
            }
        }

        public class DirHandler extends BasicUnmarshaller implements ProActiveDescriptorConstants {
            @Override
            public void startContextElement(String name, Attributes attributes) throws SAXException {
                String source = attributes.getValue("src");
                String dest = attributes.getValue("dest");
                String include = attributes.getValue("include");
                String exclude = attributes.getValue("exclude");

                if (!checkNonEmpty(source)) {
                    throw new org.xml.sax.SAXException("Source filename not specified for file tag");
                }

                if (!checkNonEmpty(dest)) {
                    dest = source;
                }
                if (!checkNonEmpty(include)) {
                    include = "*";
                }
                if (!checkNonEmpty(exclude)) {
                    exclude = "";
                }

                //fileTransfer variable is in the parent class
                fileTransfer.addDir(source, dest, include, exclude);
            }
        }
    }
}
