package org.roda.core.plugins.plugins.ingest.migration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.roda.core.data.exceptions.InvalidParameterException;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.File;
import org.roda.core.data.v2.ip.IndexedFile;
import org.roda.core.data.v2.ip.Representation;
import org.roda.core.data.v2.ip.StoragePath;
import org.roda.core.data.v2.jobs.PluginParameter;
import org.roda.core.data.v2.jobs.PluginType;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.index.IndexService;
import org.roda.core.model.ModelService;
import org.roda.core.model.utils.ModelUtils;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.storage.Binary;
import org.roda.core.storage.ContentPayload;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.fs.FSPathContentPayload;
import org.roda.core.storage.fs.FSUtils;
import org.roda.core.util.CommandException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractConvertPlugin implements Plugin<AIP> {

  public final Logger logger = LoggerFactory.getLogger(getClass());
  public String inputFormat;
  public String outputFormat;
  public long maxKbytes = 20000;
  public boolean hasPartialSuccessOnOutcome = true;

  public abstract void init() throws PluginException;

  public abstract void shutdown();

  public abstract String getName();

  public abstract String getDescription();

  public abstract String getVersion();

  public abstract Plugin<AIP> cloneMe();

  public PluginType getType() {
    return PluginType.AIP_TO_AIP;
  }

  public boolean areParameterValuesValid() {
    return true;
  }

  public List<PluginParameter> getParameters() {
    return new ArrayList<>();
  }

  public Map<String, String> getParameterValues() {
    Map<String, String> parametersMap = new HashMap<String, String>();
    parametersMap.put("inputFormat", inputFormat);
    parametersMap.put("outputFormat", outputFormat);
    parametersMap.put("maxKbytes", Long.toString(maxKbytes));
    parametersMap.put("hasPartialSuccessOnOutcome", Boolean.toString(hasPartialSuccessOnOutcome));
    return parametersMap;
  }

  public void setParameterValues(Map<String, String> parameters) throws InvalidParameterException {
    // indicates the maximum kbytes the files that will be processed must have
    if (parameters.containsKey("maxKbytes")) {
      maxKbytes = Long.parseLong(parameters.get("maxKbytes"));
    }

    // indicates outcome types: success, partial success (if true), failure
    if (parameters.containsKey("hasPartialSuccessOnOutcome")) {
      hasPartialSuccessOnOutcome = Boolean.parseBoolean(parameters.get("hasPartialSuccessOnOutcome"));
    }

    // input image format
    if (parameters.containsKey("inputFormat")) {
      inputFormat = parameters.get("inputFormat");
    }

    // output image format
    if (parameters.containsKey("outputFormat")) {
      outputFormat = parameters.get("outputFormat");
    }
  }

  public Report execute(IndexService index, ModelService model, StorageService storage, List<AIP> list)
    throws PluginException {

    for (AIP aip : list) {
      logger.debug("Processing AIP " + aip.getId());
      List<String> newRepresentations = new ArrayList<String>();
      String newRepresentationID = UUID.randomUUID().toString();

      for (Representation representation : aip.getRepresentations()) {
        List<String> alteredFiles = new ArrayList<String>();
        int state = 1;

        try {
          logger.debug("Processing representation: " + representation);

          Iterable<File> allFiles = model.listAllFiles(aip.getId(), representation.getId());

          for (File file : allFiles) {
            logger.debug("Processing file: " + file);

            if (!file.isDirectory()) {
              // TODO filter by file type and size
              // && file.getId().endsWith("." + inputFormat)
              // && (file.getSize() <= maxKbytes * 1024)
              StoragePath fileStoragePath = ModelUtils.getRepresentationFileStoragePath(file);
              Binary binary = storage.getBinary(fileStoragePath);

              // FIXME file that doesn't get deleted afterwards
              logger.debug("Running a ConvertPlugin (" + inputFormat + " to " + outputFormat + ") on " + file.getId());
              Path pluginResult = executePlugin(binary);

              if (pluginResult != null) {
                ContentPayload payload = new FSPathContentPayload(pluginResult);
                StoragePath storagePath = ModelUtils.getRepresentationPath(aip.getId(), representation.getId());

                // create a new representation if it does not exist
                if (!newRepresentations.contains(newRepresentationID)) {
                  logger.debug("Creating a new representation " + newRepresentationID + " on AIP " + aip.getId());
                  boolean original = false;
                  model.createRepresentation(aip.getId(), newRepresentationID, original, model.getStorage(),
                    storagePath);

                  StoragePath storagePreservationPath = ModelUtils.getPreservationPath(aip.getId(),
                    newRepresentationID);
                  model.getStorage().createDirectory(storagePreservationPath);
                }

                // update file on new representation
                String newFileId = file.getId().replaceFirst("[.][^.]+$", "." + outputFormat);
                model.deleteFile(aip.getId(), newRepresentationID, file.getPath(), file.getId());
                model.createFile(aip.getId(), newRepresentationID, file.getPath(), newFileId, payload);
                newRepresentations.add(newRepresentationID);
                alteredFiles.add(file.getId());

              } else {
                logger.debug("Conversion (" + inputFormat + " to " + outputFormat + ") failed on file " + file.getId()
                  + " of representation " + representation.getId() + " from AIP " + aip.getId());
                state = 2;
              }
            }
          }

        } catch (Throwable e) {
          logger.error("Error processing AIP " + aip.getId() + ": " + e.getMessage(), e);
          state = 0;
        }

        logger.debug("Creating convert plugin event for the representation " + representation.getId());
        createEvent(alteredFiles, aip, representation.getId(), newRepresentationID, model, state);
      }
    }

    return null;
  }

  public abstract Path executePlugin(Binary binary) throws UnsupportedOperationException, IOException, CommandException;

  public abstract void createEvent(List<String> alteredFiles, AIP aip, String representationID,
    String newRepresentionID, ModelService model, int state) throws PluginException;

  public abstract Report beforeExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException;

  public abstract Report afterExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException;

}