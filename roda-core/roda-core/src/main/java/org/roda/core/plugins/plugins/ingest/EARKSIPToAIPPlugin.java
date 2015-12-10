/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.plugins.ingest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.roda.core.data.Attribute;
import org.roda.core.data.PluginParameter;
import org.roda.core.data.Report;
import org.roda.core.data.ReportItem;
import org.roda.core.data.common.InvalidParameterException;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.v2.JobReport.PluginState;
import org.roda.core.data.v2.PluginType;
import org.roda.core.data.v2.TransferredResource;
import org.roda.core.index.IndexService;
import org.roda.core.model.AIP;
import org.roda.core.model.ModelService;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.plugins.plugins.PluginUtils;
import org.roda.core.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EARKSIPToAIPPlugin implements Plugin<TransferredResource> {
  private static final Logger LOGGER = LoggerFactory.getLogger(EARKSIPToAIPPlugin.class);

  private Map<String, String> parameters;

  @Override
  public void init() throws PluginException {
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  @Override
  public String getName() {
    return "EARK SIP to AIP";
  }

  @Override
  public String getDescription() {
    return "Converts a EARK SIP zip file to an AIP";
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  @Override
  public List<PluginParameter> getParameters() {
    return new ArrayList<>();
  }

  @Override
  public Map<String, String> getParameterValues() {
    return parameters;
  }

  @Override
  public void setParameterValues(Map<String, String> parameters) throws InvalidParameterException {
    this.parameters = parameters;
  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage, List<TransferredResource> list)
    throws PluginException {
    Report report = PluginUtils.createPluginReport(this);

    PluginState state;
    for (TransferredResource transferredResource : list) {
      Path earkSIPPath = Paths.get(transferredResource.getFullPath());

      ReportItem reportItem = PluginUtils.createPluginReportItem(transferredResource, this);
      try {
        LOGGER.debug("Converting " + earkSIPPath + " to AIP");
        AIP aipCreated = EARKSIPToAIPPluginUtils.earkSIPToAip(earkSIPPath, model, storage);

        reportItem.setItemId(aipCreated.getId());
        reportItem
          .addAttribute(new Attribute(RodaConstants.REPORT_ATTR_OUTCOME, RodaConstants.REPORT_ATTR_OUTCOME_SUCCESS));
        state = PluginState.OK;

        LOGGER.debug("Done with converting " + earkSIPPath + " to AIP " + aipCreated.getId());
      } catch (Throwable e) {
        reportItem.setItemId(null);
        reportItem
          .addAttribute(new Attribute(RodaConstants.REPORT_ATTR_OUTCOME, RodaConstants.REPORT_ATTR_OUTCOME_FAILURE))
          .addAttribute(new Attribute(RodaConstants.REPORT_ATTR_OUTCOME_DETAILS, e.getMessage()));
        state = PluginState.ERROR;

        LOGGER.error("Error converting " + earkSIPPath + " to AIP", e);
      }
      report.addItem(reportItem);
      PluginUtils.createJobReport(model, this, reportItem, state, PluginUtils.getJobId(parameters));
    }
    return report;
  }

  @Override
  public Report beforeExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {

    return null;
  }

  @Override
  public Report afterExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {

    return null;
  }

  @Override
  public Plugin<TransferredResource> cloneMe() {
    return new EARKSIPToAIPPlugin();
  }

  @Override
  public PluginType getType() {
    return PluginType.SIP_TO_AIP;
  }

  @Override
  public boolean areParameterValuesValid() {
    return true;
  }

}