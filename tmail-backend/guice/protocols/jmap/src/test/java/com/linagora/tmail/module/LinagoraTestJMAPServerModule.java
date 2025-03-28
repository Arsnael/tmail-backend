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
 ********************************************************************/

package com.linagora.tmail.module;

import java.io.FileNotFoundException;
import java.util.Map;

import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Provides;
import com.google.inject.name.Named;

public class LinagoraTestJMAPServerModule extends TestJMAPServerModule {

    private final Map<String, Object> overrideJmapProperties;

    public LinagoraTestJMAPServerModule(Map<String, Object> overrideJmapProperties) {
        this.overrideJmapProperties = overrideJmapProperties;
    }

    public LinagoraTestJMAPServerModule() {
        this(ImmutableMap.of());
    }

    @Provides
    @Singleton
    @Named("jmap")
    Configuration provideConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        Configuration configuration = loadConfigurationOrDefault(propertiesProvider);
        overrideJmapProperties.forEach(configuration::setProperty);
        return configuration;
    }

    private Configuration loadConfigurationOrDefault(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return propertiesProvider.getConfiguration("jmap");
        } catch (FileNotFoundException e) {
            return createDefaultConfiguration();
        }
    }

    private Configuration createDefaultConfiguration() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("calendarEvent.reply.mailTemplateLocation", "classpath://eml/");
        configuration.addProperty("url.prefix", "http://localhost:8000");
        return configuration;
    }
}