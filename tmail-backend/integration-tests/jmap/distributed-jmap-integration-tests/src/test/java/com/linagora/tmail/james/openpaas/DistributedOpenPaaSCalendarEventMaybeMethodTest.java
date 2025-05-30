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

package com.linagora.tmail.james.openpaas;

import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.DockerOpenPaasExtension;
import com.linagora.tmail.DockerOpenPaasSetup;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.LinagoraCalendarEventAcceptMethodContract;
import com.linagora.tmail.james.common.LinagoraCalendarEventMaybeMethodContract;
import com.linagora.tmail.james.common.User;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedOpenPaaSCalendarEventMaybeMethodTest extends LinagoraCalendarEventMaybeMethodContract {
    @RegisterExtension
    @Order(1)
    static DockerOpenPaasExtension openPaasExtension = new DockerOpenPaasExtension(DockerOpenPaasSetup.SINGLETON);

    @RegisterExtension
    @Order(2)
    static JamesServerExtension
        testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationPath(OpenpaasTestUtils.setupConfigurationPath(tmpDir))
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .noSecondaryS3BlobStore()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .openPassModuleChooserConfiguration(
                new OpenPaasModuleChooserConfiguration(true, true, true))
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule(), new DelegationProbeModule())
            .overrideWith(openPaasExtension.openpaasModule()))
        .build();

    @Override
    @Disabled("Email sending is handled by Dav server; we do not test Dav server internals.")
    public void shouldSendReplyMailToInvitor(GuiceJamesServer server) {

    }

    @Override
    @Disabled("Email sending is handled by Dav server; we do not test Dav server internals.")
    public void mailReplyShouldSupportI18nWhenLanguageRequest(GuiceJamesServer server) {

    }

    @Override
    @Disabled("This test validates the logic in CalendarEventReplyGenerator, which is not used for CalDav.")
    public void shouldNotMaybeWhenInvalidIcsPayload(GuiceJamesServer server) {

    }

    @Override
    public String randomBlobId() {
        return Uuids.timeBased().toString();
    }

    @Override
    public List<User> createUsers() {
        return ImmutableList.of(createUser(), createUser(), createUser(), createUser());
    }

    private User createUser() {
        return DockerOpenPaasSetup.SINGLETON
            .getOpenPaaSProvisioningService()
            .createUser()
            .map(openPaasUser -> new User(openPaasUser.firstname() + " " + openPaasUser.lastname(),
                openPaasUser.email(), LinagoraCalendarEventAcceptMethodContract.PASSWORD()))
            .block();
    }
}
