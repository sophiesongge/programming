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
package org.objectweb.proactive.extensions.dataspaces.vfs;

import org.apache.commons.logging.impl.Log4JLogger;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.cache.NullFilesCache;
import org.apache.commons.vfs2.impl.DefaultFileReplicator;
import org.apache.commons.vfs2.impl.DefaultFileSystemManager;
import org.apache.commons.vfs2.impl.PrivilegedFileReplicator;
import org.apache.commons.vfs2.provider.ftp.FtpFileProvider;
import org.apache.commons.vfs2.provider.http.HttpFileProvider;
import org.apache.commons.vfs2.provider.https.HttpsFileProvider;
import org.apache.commons.vfs2.provider.local.DefaultLocalFileProvider;
import org.apache.commons.vfs2.provider.sftp.SftpFileProvider;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.url.UrlFileProvider;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.objectweb.proactive.core.util.log.Loggers;
import org.objectweb.proactive.core.util.log.ProActiveLogger;
import org.objectweb.proactive.extensions.vfsprovider.client.ProActiveFileName;
import org.objectweb.proactive.extensions.vfsprovider.client.ProActiveFileProvider;
import org.objectweb.proactive.extensions.vfsprovider.protocol.FileSystemServer;

import com.github.vfss3.S3FileProvider;
import com.github.vfss3.operations.S3FileOperationsProvider;


/**
 * Factory class for creating configured VFS DefaultFileSystemManager instances.
 * <p>
 * Instances of managers created through this factory are guaranteed to have providers for following
 * protocols:
 * <ul>
 * <li>local files, scheme <code>file:</code></li>
 * <li>HTTP, scheme <code>http:</code></li>
 * <li>HTTPS, scheme <code>https:</code></li>
 * <li>FTP, scheme <code>ftp:</code></li>
 * <li>SFTP, scheme <code>sftp:</code> (with strict host-key checking disabled by default if no
 * other FileSystemOptions are provided)</li>
 * <li>ProActive protocol (see {@link FileSystemServer}), schemes acquired from
 * {@link ProActiveFileName#getAllVFSSchemes()}</li>
 * <li>default URL provider handled by Java URL class</code>
 * </ul>
 *
 * Configured replicator, temporary storage and default files cache are also guaranteed.
 */
public class VFSFactory {
    private static final Log4JLogger logger;

    static {
        final Logger rawLogger = ProActiveLogger.getLogger(Loggers.DATASPACES_VFS);
        if (rawLogger.getEffectiveLevel().isGreaterOrEqual(Level.INFO)) {
            // quite VFS a bit, as it is more verbose than ProActive conventions
            rawLogger.setLevel(Level.WARN);
        }
        logger = new Log4JLogger(rawLogger);
    }

    /**
     * Creates new DefaultSystemManager instance with configured providers, replicator, temporary
     * storage and files cache - as described in class description.
     * <p>
     * Returned instance is initialized and it is a caller responsibility to close it to release
     * resources.
     *
     * @return configured and initialized DefaultFileSystemManager instance
     * @throws FileSystemException
     *             when initialization or configuration process fails
     */
    public static DefaultFileSystemManager createDefaultFileSystemManager() throws FileSystemException {
        return createDefaultFileSystemManager(true);
    }

    /**
     * Creates new DefaultSystemManager instance with configured providers, replicator, temporary
     * storage - as described in class description, and <strong>DISABLED files cache</strong>
     * (NullFilesCache) .
     * <p>
     * Returned instance is initialized and it is a caller responsibility to close it to release
     * resources.
     *
     * @param enableFilesCache
     *            when <code>true</code> DefaultFilesCache is configured for returned manager; when
     *            <code>false</code> file caching is disabled - NullFilesCache is configured
     * @return configured and initialized DefaultFileSystemManager instance
     * @throws FileSystemException
     *             when initialization or configuration process fails
     */
    public static DefaultFileSystemManager createDefaultFileSystemManager(boolean enableFilesCache)
            throws FileSystemException {
        logger.debug("Creating new VFS manager");

        FileSystemOptions opts = createDefaultFileSystemOptions();
        final DefaultFileSystemManager manager = new DefaultOptionsFileSystemManager(opts);
        manager.setLogger(logger);

        final DefaultFileReplicator replicator = new DefaultFileReplicator();
        manager.setReplicator(new PrivilegedFileReplicator(replicator));
        manager.setTemporaryFileStore(replicator);
        if (!enableFilesCache) {
            // WISH: one beautiful day one may try to use FilesCache aware of AO instead of NullFilesCache
            manager.setFilesCache(new NullFilesCache());
        }

        manager.addProvider("file", new DefaultLocalFileProvider());
        manager.addProvider("http", new HttpFileProvider());
        manager.addProvider("https", new HttpsFileProvider());
        manager.addProvider("ftp", new FtpFileProvider());
        manager.addProvider("sftp", new SftpFileProvider());
        manager.addProvider("s3", new S3FileProvider());
        final ProActiveFileProvider proactiveProvider = new ProActiveFileProvider();
        for (final String scheme : ProActiveFileName.getAllVFSSchemes()) {
            manager.addProvider(scheme, proactiveProvider);
        }

        manager.addOperationProvider("s3", new S3FileOperationsProvider());

        manager.setDefaultProvider(new UrlFileProvider());

        manager.init();
        logger.debug("Created and initialized new VFS manager");
        return manager;
    }

    private static FileSystemOptions createDefaultFileSystemOptions() throws FileSystemException {
        final FileSystemOptions options = new FileSystemOptions();
        // TODO or try to configure known hosts somehow (look for OpenSSH file etc.)
        SftpFileSystemConfigBuilder.getInstance().setStrictHostKeyChecking(options, "no");
        return options;
    }
}
