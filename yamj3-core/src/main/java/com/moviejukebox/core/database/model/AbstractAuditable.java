package com.moviejukebox.core.database.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import com.moviejukebox.core.hibernate.Auditable;
import com.moviejukebox.core.hibernate.Identifiable;

/**
 * Abstract implementation of an identifiable and auditable object.
 */
@MappedSuperclass
public abstract class AbstractAuditable implements Auditable, Identifiable {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private long id;

	@Temporal(value = TemporalType.TIMESTAMP)
	@Column(name = "createTimestamp", nullable = false, updatable = false)
	private Date createTimestamp;

	@Temporal(value = TemporalType.TIMESTAMP)
	@Column(name = "updateTimestamp")
	private Date updateTimestamp;

    @Override
	public long getId() {
		return this.id;
	}

	@SuppressWarnings("unused")
	private void setId(long id) {
		this.id = id;
	}

	public boolean isNewlyCreated() {
		return (this.id <= 0);
	}

	public Date getCreateTimestamp() {
		return this.createTimestamp;
	}

	public void setCreateTimestamp(final Date createTimestamp) {
		this.createTimestamp = createTimestamp;
	}

	public Date getUpdateTimestamp() {
		return this.updateTimestamp;
	}

	public void setUpdateTimestamp(final Date updateTimestamp) {
		this.updateTimestamp = updateTimestamp;
	}
}