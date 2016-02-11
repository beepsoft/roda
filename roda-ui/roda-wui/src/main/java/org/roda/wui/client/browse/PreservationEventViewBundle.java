/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.client.browse;

import java.io.Serializable;
import java.util.List;

import org.roda.core.data.v2.ip.metadata.IndexedPreservationAgent;
import org.roda.core.data.v2.ip.metadata.IndexedPreservationEvent;

public class PreservationEventViewBundle implements Serializable {

  private static final long serialVersionUID = -8540304346599230183L;
  private IndexedPreservationEvent event;
  private List<IndexedPreservationAgent> agents;

  public PreservationEventViewBundle() {
    super();
  }

  public PreservationEventViewBundle(IndexedPreservationEvent event, List<IndexedPreservationAgent> agents) {
    super();
    this.event = event;
    this.agents = agents;
  }

  public IndexedPreservationEvent getEvent() {
    return event;
  }

  public void setEvent(IndexedPreservationEvent event) {
    this.event = event;
  }

  public List<IndexedPreservationAgent> getAgents() {
    return agents;
  }

  public void setAgents(List<IndexedPreservationAgent> agents) {
    this.agents = agents;
  }

  @Override
  public String toString() {
    return "PreservationEventViewBundle [event=" + event + ", agents=" + agents + "]";
  }

}