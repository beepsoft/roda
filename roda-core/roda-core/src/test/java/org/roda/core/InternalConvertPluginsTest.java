/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core;

import java.io.IOException;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.ext.com.google.common.collect.Iterables;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.roda.core.common.monitor.FolderMonitorNIO;
import org.roda.core.common.monitor.FolderObserver;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.adapter.filter.SimpleFilterParameter;
import org.roda.core.data.adapter.sublist.Sublist;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.AlreadyExistsException;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.InvalidParameterException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RODAException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.index.IndexResult;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.File;
import org.roda.core.data.v2.ip.IndexedAIP;
import org.roda.core.data.v2.ip.Representation;
import org.roda.core.data.v2.ip.TransferredResource;
import org.roda.core.index.IndexService;
import org.roda.core.model.ModelService;
import org.roda.core.model.ModelServiceTest;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.plugins.ingest.TransferredResourceToAIPPlugin;
import org.roda.core.plugins.plugins.ingest.migration.FfmpegConvertPlugin;
import org.roda.core.plugins.plugins.ingest.migration.GhostScriptConvertPlugin;
import org.roda.core.plugins.plugins.ingest.migration.ImageMagickConvertPlugin;
import org.roda.core.plugins.plugins.ingest.migration.JodConverterPlugin;
import org.roda.core.plugins.plugins.ingest.migration.MencoderConvertPlugin;
import org.roda.core.plugins.plugins.ingest.migration.PdfToPdfaPlugin;
import org.roda.core.plugins.plugins.ingest.migration.SoxConvertPlugin;
import org.roda.core.plugins.plugins.ingest.migration.UnoconvConvertPlugin;
import org.roda.core.storage.ClosableIterable;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.fs.FSUtils;
import org.roda.core.storage.fs.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalConvertPluginsTest {

  private static final int AUTO_COMMIT_TIMEOUT = 2000;
  private static Path basePath;
  private static Path logPath;
  private static ModelService model;
  private static IndexService index;
  private static int numberOfFilesSimple = 25;
  private static int numberOfFilesSubFolder = 27;
  private static String maxKbytes = "20000";
  private static Path corporaPath;
  private static StorageService corporaService;

  private static int SIMPLE_CORPORA = 1;
  private static int MULTIPLE_CORPORA = 2;
  private static int SUBFOLDER_CORPORA = 3;

  private static final Logger logger = LoggerFactory.getLogger(ModelServiceTest.class);

  @Before
  public void setUp() throws Exception {

    basePath = Files.createTempDirectory("indexTests");
    System.setProperty("roda.home", basePath.toString());

    boolean deploySolr = true;
    boolean deployLdap = false;
    boolean deployFolderMonitor = true;
    boolean deployOrchestrator = true;
    RodaCoreFactory.instantiateTest(deploySolr, deployLdap, deployFolderMonitor, deployOrchestrator);
    logPath = RodaCoreFactory.getLogPath();
    model = RodaCoreFactory.getModelService();
    index = RodaCoreFactory.getIndexService();

    URL corporaURL = InternalConvertPluginsTest.class.getResource("/corpora");
    corporaPath = Paths.get(corporaURL.toURI());
    corporaService = new FileStorageService(corporaPath);

    logger.info("Running internal plugins tests under storage {}", basePath);
  }

  @After
  public void tearDown() throws Exception {
    RodaCoreFactory.shutdown();
    FSUtils.deletePath(basePath);
  }

  private List<TransferredResource> createCorpora(int corporaNumber) throws InterruptedException, IOException,
    FileAlreadyExistsException, NotFoundException, GenericException, AlreadyExistsException {
    FolderMonitorNIO f = RodaCoreFactory.getFolderMonitor();

    FolderObserver observer = Mockito.mock(FolderObserver.class);
    f.addFolderObserver(observer);

    while (!f.isFullyInitialized()) {
      logger.info("Waiting for folder monitor to initialize...");
      Thread.sleep(1000);
    }

    Assert.assertTrue(f.isFullyInitialized());

    List<TransferredResource> resources = new ArrayList<TransferredResource>();
    Path corpora = null;

    if (corporaNumber == SIMPLE_CORPORA) {
      corpora = corporaPath.resolve(RodaConstants.STORAGE_CONTAINER_AIP)
        .resolve(CorporaConstants.SOURCE_AIP_CONVERTER_1).resolve(RodaConstants.STORAGE_DIRECTORY_DATA)
        .resolve(CorporaConstants.REPRESENTATION_CONVERTER_ID);
    }
    if (corporaNumber == MULTIPLE_CORPORA) {
      corpora = corporaPath.resolve(RodaConstants.STORAGE_CONTAINER_AIP)
        .resolve(CorporaConstants.SOURCE_AIP_CONVERTER_2).resolve(RodaConstants.STORAGE_DIRECTORY_DATA)
        .resolve(CorporaConstants.REPRESENTATION_CONVERTER_ID_2);
    }
    if (corporaNumber == SUBFOLDER_CORPORA) {
      corpora = corporaPath.resolve(RodaConstants.STORAGE_CONTAINER_AIP)
        .resolve(CorporaConstants.SOURCE_AIP_CONVERTER_3).resolve(RodaConstants.STORAGE_DIRECTORY_DATA)
        .resolve(CorporaConstants.REPRESENTATION_CONVERTER_ID_3);
    }

    FSUtils.copy(corpora, f.getBasePath().resolve("test"), true);

    logger.info("Waiting for soft-commit");
    Thread.sleep(AUTO_COMMIT_TIMEOUT);

    resources.add(index.retrieve(TransferredResource.class, "test"));
    return resources;
  }

  private AIP ingestCorpora(int corporaNumber) throws RequestNotValidException, NotFoundException, GenericException,
    AlreadyExistsException, AuthorizationDeniedException, InvalidParameterException, InterruptedException, IOException,
    FileAlreadyExistsException {
    AIP root = model.createAIP(null);

    Plugin<TransferredResource> plugin = new TransferredResourceToAIPPlugin();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(RodaConstants.PLUGIN_PARAMS_PARENT_ID, root.getId());
    plugin.setParameterValues(parameters);

    List<TransferredResource> transferredResources = new ArrayList<TransferredResource>();
    transferredResources = createCorpora(corporaNumber);

    Assert.assertEquals(1, transferredResources.size());
    RodaCoreFactory.getPluginOrchestrator().runPluginOnTransferredResources(plugin, transferredResources);

    IndexResult<IndexedAIP> find = index.find(IndexedAIP.class, new Filter(new SimpleFilterParameter(
      RodaConstants.AIP_PARENT_ID, root.getId())), null, new Sublist(0, 10));

    Assert.assertEquals(1L, find.getTotalCount());
    IndexedAIP indexedAIP = find.getResults().get(0);

    AIP aip = model.retrieveAIP(indexedAIP.getId());
    return aip;
  }

  @Test
  public void testIngestTransferredResource() throws IOException, InterruptedException, RODAException {
    AIP aip = ingestCorpora(SIMPLE_CORPORA);
    Assert.assertEquals(1, aip.getRepresentations().size());

    ClosableIterable<File> allFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(0).getId());
    List<File> reusableAllFiles = new ArrayList<>();
    Iterables.addAll(reusableAllFiles, allFiles);

    Assert.assertEquals(numberOfFilesSimple, reusableAllFiles.size());
  }

  @Ignore
  @Test
  public void testImageMagickPlugin() throws RODAException, FileAlreadyExistsException, InterruptedException,
    IOException {
    AIP aip = ingestCorpora(SIMPLE_CORPORA);

    ClosableIterable<File> allFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(0).getId());
    List<File> reusableAllFiles = new ArrayList<>();
    Iterables.addAll(reusableAllFiles, allFiles);

    Plugin<?> plugin = new ImageMagickConvertPlugin();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(RodaConstants.PLUGIN_PARAMS_JOB_ID, "NONE");
    parameters.put("maxKbytes", "20000");
    parameters.put("outputFormat", "tiff");
    plugin.setParameterValues(parameters);

    RodaCoreFactory.getPluginOrchestrator().runPluginOnAllRepresentations((Plugin<Representation>) plugin);

    aip = model.retrieveAIP(aip.getId());
    Assert.assertEquals(2, aip.getRepresentations().size());

    ClosableIterable<File> newAllFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(1).getId());
    List<File> newReusableAllFiles = new ArrayList<>();
    Iterables.addAll(newReusableAllFiles, newAllFiles);

    Assert.assertEquals(numberOfFilesSimple, newReusableAllFiles.size());

    int changedCounter = 0;

    for (File f : reusableAllFiles) {
      if (f.getId().matches(".*[.](jpg|png)$")) {
        changedCounter++;
        String filename = f.getId().substring(0, f.getId().lastIndexOf('.'));
        Assert.assertEquals(1, newReusableAllFiles.stream().filter(o -> o.getId().equals(filename + ".tiff")).count());
      }
    }

    Assert.assertEquals(changedCounter, newReusableAllFiles.stream().filter(o -> o.getId().matches(".*[.]tiff$"))
      .count());
  }

  @Ignore
  @Test
  public void testSoxPlugin() throws RODAException, FileAlreadyExistsException, InterruptedException, IOException {
    AIP aip = ingestCorpora(SIMPLE_CORPORA);

    ClosableIterable<File> allFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(0).getId());
    List<File> reusableAllFiles = new ArrayList<>();
    Iterables.addAll(reusableAllFiles, allFiles);

    Plugin<?> plugin = new SoxConvertPlugin();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(RodaConstants.PLUGIN_PARAMS_JOB_ID, "NONE");
    parameters.put("maxKbytes", "20000");
    parameters.put("outputFormat", "ogg");
    plugin.setParameterValues(parameters);

    RodaCoreFactory.getPluginOrchestrator().runPluginOnAllRepresentations((Plugin<Representation>) plugin);

    aip = model.retrieveAIP(aip.getId());
    Assert.assertEquals(2, aip.getRepresentations().size());

    ClosableIterable<File> newAllFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(1).getId());
    List<File> newReusableAllFiles = new ArrayList<>();
    Iterables.addAll(newReusableAllFiles, newAllFiles);

    Assert.assertEquals(numberOfFilesSimple, newReusableAllFiles.size());

    int changedCounter = 0;

    for (File f : reusableAllFiles) {
      if (f.getId().matches(".*[.](mp3)$")) {
        changedCounter++;
        String filename = f.getId().substring(0, f.getId().lastIndexOf('.'));
        Assert.assertEquals(1, newReusableAllFiles.stream().filter(o -> o.getId().equals(filename + ".ogg")).count());
      }
    }

    Assert.assertEquals(changedCounter, newReusableAllFiles.stream().filter(o -> o.getId().matches(".*[.]ogg$"))
      .count());
  }

  @Ignore
  @Test
  public void testFfmpegPlugin() throws RODAException, FileAlreadyExistsException, InterruptedException, IOException {
    AIP aip = ingestCorpora(SIMPLE_CORPORA);

    ClosableIterable<File> allFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(0).getId());
    List<File> reusableAllFiles = new ArrayList<>();
    Iterables.addAll(reusableAllFiles, allFiles);

    Plugin<?> plugin = new FfmpegConvertPlugin();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(RodaConstants.PLUGIN_PARAMS_JOB_ID, "NONE");
    parameters.put("maxKbytes", "20000");
    parameters.put("outputFormat", "gif");
    plugin.setParameterValues(parameters);

    RodaCoreFactory.getPluginOrchestrator().runPluginOnAllRepresentations((Plugin<Representation>) plugin);

    aip = model.retrieveAIP(aip.getId());
    Assert.assertEquals(2, aip.getRepresentations().size());

    ClosableIterable<File> newAllFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(1).getId());
    List<File> newReusableAllFiles = new ArrayList<>();
    Iterables.addAll(newReusableAllFiles, newAllFiles);

    Assert.assertEquals(numberOfFilesSimple, newReusableAllFiles.size());

    int changedCounter = 0;

    for (File f : reusableAllFiles) {
      if (f.getId().matches(".*[.](3g2|avi)$")) {
        changedCounter++;
        String filename = f.getId().substring(0, f.getId().lastIndexOf('.'));
        Assert.assertEquals(1, newReusableAllFiles.stream().filter(o -> o.getId().equals(filename + ".gif")).count());
      }
    }

    Assert.assertEquals(changedCounter, newReusableAllFiles.stream().filter(o -> o.getId().matches(".*[.]gif$"))
      .count());
  }

  @Ignore
  @Test
  public void testUnoconvPlugin() throws RODAException, FileAlreadyExistsException, InterruptedException, IOException {
    AIP aip = ingestCorpora(SIMPLE_CORPORA);

    ClosableIterable<File> allFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(0).getId());
    List<File> reusableAllFiles = new ArrayList<>();
    Iterables.addAll(reusableAllFiles, allFiles);

    Plugin<?> plugin = new UnoconvConvertPlugin();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(RodaConstants.PLUGIN_PARAMS_JOB_ID, "NONE");
    parameters.put("maxKbytes", "20000");
    parameters.put("outputFormat", "pdf");
    plugin.setParameterValues(parameters);

    RodaCoreFactory.getPluginOrchestrator().runPluginOnAllRepresentations((Plugin<Representation>) plugin);

    aip = model.retrieveAIP(aip.getId());
    Assert.assertEquals(2, aip.getRepresentations().size());

    ClosableIterable<File> newAllFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(1).getId());
    List<File> newReusableAllFiles = new ArrayList<>();
    Iterables.addAll(newReusableAllFiles, newAllFiles);

    Assert.assertEquals(numberOfFilesSimple, newReusableAllFiles.size());

    int changedCounter = 0;

    for (File f : reusableAllFiles) {
      if (f.getId().matches(".*[.](pdf|docx|txt|xls|odp|ppt|pptx|doc|rtf|xlsx|ods|odt)$")) {
        changedCounter++;
        String filename = f.getId().substring(0, f.getId().lastIndexOf('.'));
        Assert.assertEquals(1, newReusableAllFiles.stream().filter(o -> o.getId().equals(filename + ".pdf")).count());
      }
    }

    Assert.assertEquals(changedCounter, newReusableAllFiles.stream().filter(o -> o.getId().matches(".*[.]pdf$"))
      .count());
  }

  @Ignore
  @Test
  public void testMencoderPlugin() throws RODAException, FileAlreadyExistsException, InterruptedException, IOException {
    AIP aip = ingestCorpora(SIMPLE_CORPORA);

    ClosableIterable<File> allFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(0).getId());
    List<File> reusableAllFiles = new ArrayList<>();
    Iterables.addAll(reusableAllFiles, allFiles);

    Plugin<?> plugin = new MencoderConvertPlugin();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(RodaConstants.PLUGIN_PARAMS_JOB_ID, "NONE");
    parameters.put("maxKbytes", "20000");
    parameters.put("outputFormat", "avi");
    plugin.setParameterValues(parameters);

    RodaCoreFactory.getPluginOrchestrator().runPluginOnAllRepresentations((Plugin<Representation>) plugin);

    aip = model.retrieveAIP(aip.getId());
    Assert.assertEquals(2, aip.getRepresentations().size());

    ClosableIterable<File> newAllFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(1).getId());
    List<File> newReusableAllFiles = new ArrayList<>();
    Iterables.addAll(newReusableAllFiles, newAllFiles);

    Assert.assertEquals(numberOfFilesSimple, newReusableAllFiles.size());

    int changedCounter = 0;

    for (File f : reusableAllFiles) {
      if (f.getId().matches(".*[.](avi|mpg)$")) {
        changedCounter++;
        String filename = f.getId().substring(0, f.getId().lastIndexOf('.'));
        Assert.assertEquals(1, newReusableAllFiles.stream().filter(o -> o.getId().equals(filename + ".avi")).count());
      }
    }

    Assert.assertEquals(changedCounter, newReusableAllFiles.stream().filter(o -> o.getId().matches(".*[.]avi$"))
      .count());
  }

  @Ignore
  @Test
  public void testGhostScriptPlugin() throws RODAException, FileAlreadyExistsException, InterruptedException,
    IOException {
    AIP aip = ingestCorpora(SIMPLE_CORPORA);

    ClosableIterable<File> allFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(0).getId());
    List<File> reusableAllFiles = new ArrayList<>();
    Iterables.addAll(reusableAllFiles, allFiles);

    Plugin<?> plugin = new GhostScriptConvertPlugin();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(RodaConstants.PLUGIN_PARAMS_JOB_ID, "NONE");
    parameters.put("maxKbytes", "20000");
    parameters.put("outputFormat", "pdf");
    parameters.put("commandArguments", "-sDevice=pdfwrite");
    plugin.setParameterValues(parameters);

    RodaCoreFactory.getPluginOrchestrator().runPluginOnAllRepresentations((Plugin<Representation>) plugin);

    aip = model.retrieveAIP(aip.getId());
    Assert.assertEquals(2, aip.getRepresentations().size());

    ClosableIterable<File> newAllFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(1).getId());
    List<File> newReusableAllFiles = new ArrayList<>();
    Iterables.addAll(newReusableAllFiles, newAllFiles);

    Assert.assertEquals(numberOfFilesSimple, newReusableAllFiles.size());

    int changedCounter = 0;

    for (File f : reusableAllFiles) {
      if (f.getId().matches(".*[.](pdf)$")) {
        changedCounter++;
        String filename = f.getId().substring(0, f.getId().lastIndexOf('.'));
        Assert.assertEquals(1, newReusableAllFiles.stream().filter(o -> o.getId().equals(filename + ".pdf")).count());
      }
    }

    Assert.assertEquals(changedCounter, newReusableAllFiles.stream().filter(o -> o.getId().matches(".*[.]pdf$"))
      .count());
  }

  @Ignore
  @Test
  public void testJodConverterPlugin() throws RODAException, FileAlreadyExistsException, InterruptedException,
    IOException {
    AIP aip = ingestCorpora(SIMPLE_CORPORA);

    ClosableIterable<File> allFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(0).getId());
    List<File> reusableAllFiles = new ArrayList<>();
    Iterables.addAll(reusableAllFiles, allFiles);

    Plugin<?> plugin = new JodConverterPlugin();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(RodaConstants.PLUGIN_PARAMS_JOB_ID, "NONE");
    parameters.put("maxKbytes", "20000");
    parameters.put("outputFormat", "pdf");
    plugin.setParameterValues(parameters);

    // XXX needs soffice running
    RodaCoreFactory.getPluginOrchestrator().runPluginOnAllRepresentations((Plugin<Representation>) plugin);

    aip = model.retrieveAIP(aip.getId());
    Assert.assertEquals(2, aip.getRepresentations().size());

    ClosableIterable<File> newAllFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(1).getId());
    List<File> newReusableAllFiles = new ArrayList<>();
    Iterables.addAll(newReusableAllFiles, newAllFiles);

    Assert.assertEquals(numberOfFilesSimple, newReusableAllFiles.size());

    int changedCounter = 0;

    for (File f : reusableAllFiles) {
      if (f.getId().matches(".*[.](pdf|txt|doc|xls|ppt|html|odt|ods|odp)$")) {
        changedCounter++;
        String filename = f.getId().substring(0, f.getId().lastIndexOf('.'));
        Assert.assertEquals(1, newReusableAllFiles.stream().filter(o -> o.getId().equals(filename + ".pdf")).count());
      }
    }

    Assert.assertEquals(changedCounter, newReusableAllFiles.stream().filter(o -> o.getId().matches(".*[.]pdf$"))
      .count());
  }

  @Ignore
  @Test
  public void testPdfToPdfaPlugin() throws RODAException, FileAlreadyExistsException, InterruptedException, IOException {
    AIP aip = ingestCorpora(SIMPLE_CORPORA);

    ClosableIterable<File> allFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(0).getId());
    List<File> reusableAllFiles = new ArrayList<>();
    Iterables.addAll(reusableAllFiles, allFiles);

    Plugin<?> plugin = new PdfToPdfaPlugin();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(RodaConstants.PLUGIN_PARAMS_JOB_ID, "NONE");
    parameters.put("maxKbytes", "20000");
    parameters.put("outputFormat", "pdf");
    plugin.setParameterValues(parameters);

    // XXX needs soffice running
    RodaCoreFactory.getPluginOrchestrator().runPluginOnAllRepresentations((Plugin<Representation>) plugin);

    aip = model.retrieveAIP(aip.getId());
    Assert.assertEquals(2, aip.getRepresentations().size());

    ClosableIterable<File> newAllFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(1).getId());
    List<File> newReusableAllFiles = new ArrayList<>();
    Iterables.addAll(newReusableAllFiles, newAllFiles);

    Assert.assertEquals(numberOfFilesSimple, newReusableAllFiles.size());

    int changedCounter = 0;

    for (File f : reusableAllFiles) {
      if (f.getId().matches(".*[.](pdf)$")) {
        changedCounter++;
        String filename = f.getId().substring(0, f.getId().lastIndexOf('.'));
        Assert.assertEquals(1, newReusableAllFiles.stream().filter(o -> o.getId().equals(filename + ".pdf")).count());
      }
    }

    Assert.assertEquals(changedCounter, newReusableAllFiles.stream().filter(o -> o.getId().matches(".*[.]pdf$"))
      .count());
  }

  @Test
  public void testSubFolderConversion() throws FileAlreadyExistsException, RequestNotValidException, NotFoundException,
    GenericException, AlreadyExistsException, AuthorizationDeniedException, InvalidParameterException,
    InterruptedException, IOException {

    AIP aip = ingestCorpora(SUBFOLDER_CORPORA);

    ClosableIterable<File> allFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(0).getId());
    List<File> reusableAllFiles = new ArrayList<>();
    Iterables.addAll(reusableAllFiles, allFiles);

    Plugin<?> plugin = new ImageMagickConvertPlugin();
    Map<String, String> parameters = new HashMap<>();
    parameters.put(RodaConstants.PLUGIN_PARAMS_JOB_ID, "NONE");
    parameters.put("maxKbytes", "20000");
    parameters.put("outputFormat", "tiff");
    plugin.setParameterValues(parameters);

    RodaCoreFactory.getPluginOrchestrator().runPluginOnAllRepresentations((Plugin<Representation>) plugin);

    aip = model.retrieveAIP(aip.getId());
    Assert.assertEquals(2, aip.getRepresentations().size());

    ClosableIterable<File> newAllFiles = model.listAllFiles(aip.getId(), aip.getRepresentations().get(1).getId());
    List<File> newReusableAllFiles = new ArrayList<>();
    Iterables.addAll(newReusableAllFiles, newAllFiles);

    Assert.assertEquals(numberOfFilesSubFolder, newReusableAllFiles.size());

    int changedCounter = 0;

    for (File f : reusableAllFiles) {
      if (f.getId().matches(".*[.](jpg|png)$")) {
        changedCounter++;
        String filename = f.getId().substring(0, f.getId().lastIndexOf('.'));
        Assert.assertEquals(1, newReusableAllFiles.stream().filter(o -> o.getId().equals(filename + ".tiff")).count());
      }
    }

    Assert.assertEquals(changedCounter, newReusableAllFiles.stream().filter(o -> o.getId().matches(".*[.]tiff$"))
      .count());
  }

}