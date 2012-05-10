/*******************************************************************************
 * Copyright (c) 2010 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package net.bioclipse.ui.install.commands;

import java.util.Iterator;
import java.util.List;

import net.bioclipse.ui.install.discovery.BasicRepositoryDiscoveryStrategy;

import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;

/**
 * @author Steffen Pingel
 */
public class RepositoryDiscoveryStrategy extends
                BasicRepositoryDiscoveryStrategy {

    protected void queryInstallableUnits( SubMonitor monitor,
                                          List<IMetadataRepository> repositories ) {
		monitor.setWorkRemaining(repositories.size());
		for (final IMetadataRepository repository : repositories) {
			checkCancelled(monitor);
			IQuery<IInstallableUnit> query = QueryUtil.createMatchQuery(//
                            "id ~= /net.bioclipse.data.*/ " ); //$NON-NLS-1$
			IQueryResult<IInstallableUnit> result = repository.query(query, monitor.newChild(1));
			for (Iterator<IInstallableUnit> iter = result.iterator(); iter.hasNext();) {
				process(repository, iter.next());
			}
		}
	}

}
