/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.index;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.roda.core.common.iterables.CloseableIterable;
import org.roda.core.data.adapter.facet.Facets;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.adapter.sort.Sorter;
import org.roda.core.data.adapter.sublist.Sublist;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.index.IndexResult;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.IndexedAIP;
import org.roda.core.data.v2.jobs.Job;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.data.v2.log.LogEntry;
import org.roda.core.index.utils.SolrUtils;
import org.roda.core.model.ModelService;
import org.roda.core.model.utils.JsonUtils;
import org.roda.core.storage.Binary;
import org.roda.core.storage.DefaultStoragePath;
import org.roda.core.storage.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexService {

  private static final Logger LOGGER = LoggerFactory.getLogger(IndexService.class);

  private final SolrClient index;
  private final ModelService model;
  private final IndexModelObserver observer;

  public IndexService(SolrClient index, ModelService model) {
    super();
    this.index = index;
    this.model = model;

    observer = new IndexModelObserver(this.index, this.model);
    model.addModelObserver(observer);
  }

  public IndexedAIP getParent(IndexedAIP aip) throws NotFoundException, GenericException {
    return SolrUtils.retrieve(index, IndexedAIP.class, aip.getParentID());
  }

  public List<IndexedAIP> getAncestors(IndexedAIP aip) throws GenericException {
    List<IndexedAIP> ancestors = new ArrayList<IndexedAIP>();
    IndexedAIP parent = null, actual = aip;

    while (actual != null && actual.getParentID() != null && !ancestors.contains(actual)) {
      try {
        parent = getParent(actual);
      } catch (NotFoundException e) {
        parent = null;
        LOGGER.warn("Ancestor not found: " + actual.getParentID());
      }

      ancestors.add(parent);
      actual = parent;
    }

    return ancestors;
  }

  public <T extends Serializable> Long count(Class<T> returnClass, Filter filter)
    throws GenericException, RequestNotValidException {
    return SolrUtils.count(index, returnClass, filter);
  }

  public <T extends Serializable> IndexResult<T> find(Class<T> returnClass, Filter filter, Sorter sorter,
    Sublist sublist) throws GenericException, RequestNotValidException {
    Facets facets = null;
    return SolrUtils.find(index, returnClass, filter, sorter, sublist, facets);

  }

  public <T extends Serializable> IndexResult<T> find(Class<T> returnClass, Filter filter, Sorter sorter,
    Sublist sublist, Facets facets) throws GenericException, RequestNotValidException {
    return SolrUtils.find(index, returnClass, filter, sorter, sublist, facets);
  }

  public <T extends Serializable> T retrieve(Class<T> returnClass, String id)
    throws NotFoundException, GenericException {
    return SolrUtils.retrieve(index, returnClass, id);
  }

  public void reindexAIPs()
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    CloseableIterable<AIP> aips = null;
    try {
      LOGGER.info(new Date().getTime() + " > Listing AIPs");
      aips = model.listAIPs();
      for (AIP aip : aips) {
        if (aip != null) {
          LOGGER.info(new Date().getTime() + " > Reindexing AIP " + aip.getId());
          reindexAIP(aip);
        } else {
          LOGGER.error(new Date().getTime() + " > An error occurred. See log for more details.");
        }
      }
      LOGGER.info(new Date().getTime() + " > Optimizing indexes");
      optimizeAIPs();
      LOGGER.info(new Date().getTime() + " > Done");
    } finally {
      try {
        if (aips != null) {
          aips.close();
        }
      } catch (IOException e) {
        LOGGER.error("Error while while freeing up resources", e);
      }
    }
  }

  public void optimizeAIPs() throws GenericException {
    try {
      index.optimize(RodaConstants.INDEX_AIP);
      index.optimize(RodaConstants.INDEX_FILE);
      index.optimize(RodaConstants.INDEX_REPRESENTATION);
      index.optimize(RodaConstants.INDEX_PRESERVATION_EVENTS);
    } catch (SolrServerException | IOException e) {
      throw new GenericException("Error while optimizing indexes", e);
    }
  }

  public void reindexAIP(AIP aip) {
    observer.aipCreated(aip);
  }

  public void reindexJob(Job job) {
    observer.jobCreatedOrUpdated(job);
  }

  public void reindexJobReport(Report jobReport) {
    observer.jobReportCreatedOrUpdated(jobReport);
  }

  public void reindexActionLogs()
    throws GenericException, NotFoundException, AuthorizationDeniedException, RequestNotValidException {
    CloseableIterable<Resource> actionLogs = null;

    try {
      boolean recursive = false;
      actionLogs = model.getStorage()
        .listResourcesUnderContainer(DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_ACTIONLOG), recursive);

      for (Resource resource : actionLogs) {
        if (resource instanceof Binary) {
          Binary b = (Binary) resource;
          BufferedReader br = new BufferedReader(new InputStreamReader(b.getContent().createInputStream()));
          reindexActionLog(br);
        }
      }
    } catch (IOException e) {
      throw new GenericException("Error retrieving/processing logs from storage", e);
    } finally {
      if (actionLogs != null) {
        try {
          actionLogs.close();
        } catch (IOException e) {
          LOGGER.error("Error while while freeing up resources", e);
        }
      }
    }
  }

  private void reindexActionLog(BufferedReader br) throws GenericException {
    String line;
    try {
      while ((line = br.readLine()) != null) {
        LogEntry entry = JsonUtils.getObjectFromJson(line, LogEntry.class);
        if (entry != null) {
          reindexActionLog(entry);
        }
      }
      br.close();
    } catch (IOException e) {
      throw new GenericException("Error reading log", e);
    }
  }

  private void reindexActionLog(LogEntry entry) {
    observer.logEntryCreated(entry);
  }

  public void deleteAllActionLog() throws GenericException {
    clearIndex(RodaConstants.INDEX_ACTION_LOG);
  }

  public void deleteActionLog(Date until) throws SolrServerException, IOException {
    SimpleDateFormat iso8601DateFormat = new SimpleDateFormat(RodaConstants.SOLRDATEFORMAT);
    String dateString = iso8601DateFormat.format(until);
    String query = RodaConstants.LOG_DATETIME + ":[* TO " + dateString + "]";
    index.deleteByQuery(RodaConstants.INDEX_ACTION_LOG, query);
    index.commit(RodaConstants.INDEX_ACTION_LOG);

  }

  public void clearIndex(String indexName) throws GenericException {
    try {
      index.deleteByQuery(indexName, "*:*");
      index.commit(indexName);
    } catch (SolrServerException | IOException e) {
      LOGGER.error("Error cleaning up index " + indexName, e);
      throw new GenericException("Error cleaning up index " + indexName, e);
    }
  }

  public void optimizeIndex(String indexName) throws GenericException {
    try {
      index.optimize(indexName);
    } catch (SolrServerException | IOException e) {
      throw new GenericException("Error while optimizing indexes", e);
    }
  }

  public <T extends Serializable> List<String> suggest(Class<T> returnClass, String field, String query)
    throws GenericException {
    return SolrUtils.suggest(index, returnClass, field, query);
  }

}
