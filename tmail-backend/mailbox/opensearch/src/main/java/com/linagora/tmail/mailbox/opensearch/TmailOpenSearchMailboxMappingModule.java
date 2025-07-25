/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.mailbox.opensearch;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.mailbox.opensearch.MailboxMappingFactory;
import org.apache.james.mailbox.opensearch.query.CriterionConverter;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

public class TmailOpenSearchMailboxMappingModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(TmailOpenSearchMailboxMappingModule.class);
    private static final String OPENSEARCH_CONFIGURATION_NAME = "opensearch";

    @Override
    protected void configure() {
        bind(TmailMailboxMappingFactory.class).in(Scopes.SINGLETON);
        bind(MailboxMappingFactory.class).to(TmailMailboxMappingFactory.class);

        bind(TmailCriterionConverter.class).in(Scopes.SINGLETON);
        bind(CriterionConverter.class).to(TmailCriterionConverter.class);
    }

    @Provides
    @Singleton
    private TmailOpenSearchMailboxConfiguration getOpenSearchMailboxConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(OPENSEARCH_CONFIGURATION_NAME);
            return TmailOpenSearchMailboxConfiguration.fromProperties(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + OPENSEARCH_CONFIGURATION_NAME + " configuration file. Providing a default TmailOpenSearchMailboxConfiguration");
            return TmailOpenSearchMailboxConfiguration.DEFAULT_CONFIGURATION;
        }
    }
}
