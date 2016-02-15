package com.hp.nga.integrations.dto.snapshots.impl;

import com.hp.nga.integrations.dto.causes.CIEventCauseBase;
import com.hp.nga.integrations.dto.parameters.ParameterInstance;
import com.hp.nga.integrations.dto.scm.SCMData;
import com.hp.nga.integrations.dto.snapshots.SnapshotNode;
import com.hp.nga.integrations.dto.snapshots.SnapshotPhase;
import com.hp.nga.integrations.dto.snapshots.SnapshotResult;
import com.hp.nga.integrations.dto.snapshots.SnapshotStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: gullery
 * Date: 03/01/15
 * Time: 10:49
 * To change this template use File | Settings | File Templates.
 */

class SnapshotNodeImpl implements SnapshotNode {
	private String ciId;
	private String name;
	private Integer number = null;
	private CIEventCauseBase[] causes = null;
	private SnapshotStatus status = SnapshotStatus.UNAVAILABLE;
	private SnapshotResult result = SnapshotResult.UNAVAILABLE;
	private Long estimatedDuration = null;
	private Long startTime = null;
	private Long duration = null;
	private SCMData scmData = null;
	private List<ParameterInstance> parameters = new ArrayList<ParameterInstance>();
	private List<SnapshotPhase> phasesInternal = new ArrayList<SnapshotPhase>();
	private List<SnapshotPhase> phasesPostBuild = new ArrayList<SnapshotPhase>();

	public String getCiId() {
		return ciId;
	}

	public SnapshotNode setCiId(String ciId) {
		this.ciId = ciId;
		return this;
	}

	public String getName() {
		return name;
	}

	public SnapshotNode setName(String name) {
		this.name = name;
		return this;
	}

	public Integer getNumber() {
		return number;
	}

	public SnapshotNode setNumber(Integer number) {
		this.number = number;
		return this;
	}

	public CIEventCauseBase[] getCauses() {
		return causes;
	}

	public SnapshotNode setCauses(CIEventCauseBase[] causes) {
		this.causes = causes;
		return this;
	}

	public SnapshotStatus getStatus() {
		return status;
	}

	public SnapshotNode setStatus(SnapshotStatus status) {
		this.status = status;
		return this;
	}

	public SnapshotResult getResult() {
		return result;
	}

	public SnapshotNode setResult(SnapshotResult result) {
		this.result = result;
		return this;
	}

	public Long getEstimatedDuration() {
		return estimatedDuration;
	}

	public SnapshotNode setEstimatedDuration(Long estimatedDuration) {
		this.estimatedDuration = estimatedDuration;
		return this;
	}

	public Long getStartTime() {
		return startTime;
	}

	public SnapshotNode setStartTime(Long startTime) {
		this.startTime = startTime;
		return this;
	}

	public Long getDuration() {
		return duration;
	}

	public SnapshotNode setDuration(Long duration) {
		this.duration = duration;
		return this;
	}

	public SCMData getScmData() {
		return scmData;
	}

	public SnapshotNode setScmData(SCMData scmData) {
		this.scmData = scmData;
		return this;
	}

	public List<ParameterInstance> getParameters() {
		return parameters;
	}

	public SnapshotNode setParameters(List<ParameterInstance> parameters) {
		this.parameters = parameters;
		return this;
	}

	public List<SnapshotPhase> getPhasesInternal() {
		return phasesInternal;
	}

	public SnapshotNode setPhasesInternal(List<SnapshotPhase> phasesInternal) {
		this.phasesInternal = phasesInternal;
		return this;
	}

	public List<SnapshotPhase> getPhasesPostBuild() {
		return phasesPostBuild;
	}

	public SnapshotNode setPhasesPostBuild(List<SnapshotPhase> phasesPostBuild) {
		this.phasesPostBuild = phasesPostBuild;
		return this;
	}
}
