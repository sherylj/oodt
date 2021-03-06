/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.oodt.cas.filemgr.ingest;

//JDK imports
import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Vector;

//OODT imports
import org.apache.oodt.cas.filemgr.metadata.CoreMetKeys;
import org.apache.oodt.cas.filemgr.structs.Product;
import org.apache.oodt.cas.filemgr.structs.exceptions.CacheException;
import org.apache.oodt.cas.filemgr.system.XmlRpcFileManager;
import org.apache.oodt.cas.filemgr.system.XmlRpcFileManagerClient;
import org.apache.oodt.cas.metadata.Metadata;
import org.apache.oodt.cas.metadata.SerializableMetadata;

// Jnit imports
import org.apache.oodt.commons.util.DateConvert;
import junit.framework.TestCase;

/**
 * @author mattmann
 * @version $Revision$
 * 
 * <p>
 * Test suite for the {@link CachedIngester}.
 * </p>.
 */
public class TestCachedIngester extends TestCase {

    private static final int FM_PORT = 50010;

    private XmlRpcFileManager fm;

    private String luceneCatLoc;

    private CachedIngester ingester;

    private static final String RANGE_QUERY_ELEM = "CAS.ProductReceivedTime";

    private static final String UNIQUE_ELEM = "CAS.ProductName";

    private static final String GENERIC_FILE_TYPE = "GenericFile";

    private static String DATE_RANGE_START;

    private static String DATE_RANGE_END;

    private static List cachedProductTypes = new Vector();

    static {
        Date startDate = new Date();
        Date endDate = new Date();
        endDate.setHours(startDate.getHours() + 1);
        DATE_RANGE_START = DateConvert.isoFormat(startDate);
        DATE_RANGE_END = DateConvert.isoFormat(endDate);
        cachedProductTypes.add(GENERIC_FILE_TYPE);

    }

    private static final String transferServiceFacClass = "org.apache.oodt.cas."
            + "filemgr.datatransfer.LocalDataTransferFactory";

    public TestCachedIngester() {
    }

    public void testHasProduct() {
        try {
            ingester.resynsc();
        } catch (CacheException e) {
            fail(e.getMessage());
        }
        try {
            assertTrue(ingester.hasProduct(new URL("http://localhost:"
                    + FM_PORT), "test.txt"));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public void testIngest() {
        Metadata prodMet = null;

        try {
            prodMet = new SerializableMetadata(new FileInputStream(
                    "./src/testdata/ingest/test.txt.met"));

            // now add the right file location
            prodMet.addMetadata(CoreMetKeys.FILE_LOCATION, new File(
                    "./src/testdata/ingest").getCanonicalPath());
            ingester.ingest(new URL("http://localhost:" + FM_PORT), new File(
                    "./src/testdata/ingest/test.txt"), prodMet);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        // now make sure that the file is ingested
        try {
            XmlRpcFileManagerClient fmClient = new XmlRpcFileManagerClient(
                    new URL("http://localhost:" + FM_PORT));
            Product p = fmClient.getProductByName("test.txt");
            assertNotNull(p);
            assertEquals(Product.STATUS_RECEIVED, p.getTransferStatus());
            assertTrue(fmClient.hasProduct("test.txt"));
            fmClient = null;
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception {
        startXmlRpcFileManager();
        ingestTestFile();
        Cache cache = null;

        try {
            cache = new LocalCache(new URL("http://localhost:" + FM_PORT),
                    UNIQUE_ELEM, cachedProductTypes, RANGE_QUERY_ELEM,
                    DATE_RANGE_START, DATE_RANGE_END);
        } catch (Exception e) {
            fail("This should never happen");
        }
        try {
            ingester = new CachedIngester(transferServiceFacClass, cache);
        } catch (InstantiationException e) {
            fail(e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see junit.framework.TestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        fm.shutdown();
        fm = null;

        // blow away lucene cat
        deleteAllFiles(luceneCatLoc);

        // blow away test file
        deleteAllFiles("/tmp/test.txt");
    }

    private void ingestTestFile() {
        Metadata prodMet = null;
        StdIngester ingester = new StdIngester(transferServiceFacClass);

        try {
            prodMet = new SerializableMetadata(new FileInputStream(
                    "./src/testdata/ingest/test.txt.met"));

            // now add the right file location
            prodMet.addMetadata(CoreMetKeys.FILE_LOCATION, new File(
                    "./src/testdata/ingest").getCanonicalPath());
            ingester.ingest(new URL("http://localhost:" + FM_PORT), new File(
                    "./src/testdata/ingest/test.txt"), prodMet);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private void deleteAllFiles(String startDir) {
        File startDirFile = new File(startDir);
        File[] delFiles = startDirFile.listFiles();

        if (delFiles != null && delFiles.length > 0) {
            for (int i = 0; i < delFiles.length; i++) {
                delFiles[i].delete();
            }
        }

        startDirFile.delete();

    }

    private void startXmlRpcFileManager() {
        // first make sure to load properties for the file manager
        // and make sure to load logging properties as well

        // set the log levels
        System.setProperty("java.util.logging.config.file", new File(
                "./src/main/resources/logging.properties").getAbsolutePath());

        // first load the example configuration
        try {
            System.getProperties().load(
                    new FileInputStream("./src/main/resources/filemgr.properties"));
        } catch (Exception e) {
            fail(e.getMessage());
        }

        // override the catalog to use: we'll use lucene
        try {
            luceneCatLoc = new File("./src/testdata/ingest/cat")
                    .getCanonicalPath();
        } catch (Exception e) {
            fail(e.getMessage());
        }

        System.setProperty("filemgr.catalog.factory",
                "org.apache.oodt.cas.filemgr.catalog.LuceneCatalogFactory");
        System.setProperty(
                "org.apache.oodt.cas.filemgr.catalog.lucene.idxPath",
                luceneCatLoc);

        // now override the repo mgr policy
        try {
            System.setProperty(
                    "org.apache.oodt.cas.filemgr.repositorymgr.dirs",
                    "file://"
                            + new File("./src/testdata/ingest/fmpolicy")
                                    .getCanonicalPath());
        } catch (Exception e) {
            fail(e.getMessage());
        }

        // now override the val layer ones
        System.setProperty("org.apache.oodt.cas.filemgr.validation.dirs",
                "file://"
                        + new File("./src/main/resources/examples/core")
                                .getAbsolutePath());

        // set up mime repo path
        System.setProperty(
                "org.apache.oodt.cas.filemgr.mime.type.repository", new File(
                        "./src/main/resources/mime-types.xml").getAbsolutePath());

        try {
            fm = new XmlRpcFileManager(FM_PORT);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

}
