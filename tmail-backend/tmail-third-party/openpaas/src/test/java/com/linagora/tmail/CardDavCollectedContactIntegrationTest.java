package com.linagora.tmail;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

import jakarta.inject.Singleton;
import jakarta.mail.MessagingException;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.util.Modules;
import com.linagora.tmail.api.OpenPaasServerExtension;
import com.linagora.tmail.carddav.CardDavCreationFactory;
import com.linagora.tmail.carddav.CardDavServerExtension;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngineModule;
import com.linagora.tmail.mailet.CardDavCollectedContact;

public class CardDavCollectedContactIntegrationTest {
    private static final Username ALICE = Username.of("alice@" + DEFAULT_DOMAIN);
    private static final Username BOB = Username.of("bob@" + DEFAULT_DOMAIN);
    private static final Username CEDRIC = Username.of("cedric@" + DEFAULT_DOMAIN);
    private static final String ALICE_OPENPAAS_USER_NAME = ALICE.asString();
    private static final boolean COLLECTED_CONTACT_EXISTS = true;

    @RegisterExtension
    static OpenPaasServerExtension openPaasServerExtension = new OpenPaasServerExtension();

    @RegisterExtension
    static CardDavServerExtension cardDavServerExtension = new CardDavServerExtension();

    private TemporaryJamesServer jamesServer;

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(Modules.combine(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE,
                new OpenPaasModule()))
            .withOverrides(new InMemoryEmailAddressContactSearchEngineModule())
            .withOverrides(new AbstractModule() {
                @Override
                protected void configure() {
                   install(new OpenPaasModule.CardDavModule());
                }

                @Provides
                @Singleton
                public OpenPaasConfiguration provideOpenPaasConfiguration() {
                    return new OpenPaasConfiguration(
                        openPaasServerExtension.getBaseUrl(),
                        OpenPaasServerExtension.GOOD_USER(),
                        OpenPaasServerExtension.GOOD_PASSWORD(),
                        false,
                        cardDavServerExtension.getCardDavConfiguration());
                }
            })
            .withMailetContainer(TemporaryJamesServer.defaultMailetContainerConfiguration()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CardDavCollectedContact.class))
                    .addMailetsFrom(CommonProcessors.deliverOnlyTransport()))
            )
            .build(temporaryFolder);

        jamesServer.start();
        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(ALICE.asString(), PASSWORD);
        dataProbe.addUser(BOB.asString(), PASSWORD);
        dataProbe.addUser(CEDRIC.asString(), PASSWORD);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void shouldPUTCreateCollectedContactWhenContactDoesNotExist() throws Exception {
        // Setup mock server
        String contactUid = CardDavCreationFactory.createContactUid(BOB.asMailAddress());
        String openPassUid = UUID.randomUUID().toString();
        openPaasServerExtension.setSearchEmailExist(ALICE.asString(), openPassUid);
        // Contact does not exist
        cardDavServerExtension.setCollectedContactExists(ALICE_OPENPAAS_USER_NAME, openPassUid, contactUid, !COLLECTED_CONTACT_EXISTS);
        cardDavServerExtension.setCreateCollectedContact(ALICE_OPENPAAS_USER_NAME, openPassUid, contactUid);

        // when alice sends an email to bob
        aliceSendAnEmailToBob();

        // then the endpoint createCollectedContact is called
        cardDavServerExtension.assertCreateCollectedContactWasCalled(ALICE_OPENPAAS_USER_NAME, openPassUid, contactUid, 1);
    }

    @Test
    void shouldNotPUTCreateCollectedContactWhenContactExists() throws Exception {
        // Setup mock server
        String contactUid = CardDavCreationFactory.createContactUid(BOB.asMailAddress());
        String openPassUid = UUID.randomUUID().toString();
        openPaasServerExtension.setSearchEmailExist(ALICE.asString(), openPassUid);
        // Contact exists
        cardDavServerExtension.setCollectedContactExists(ALICE_OPENPAAS_USER_NAME, openPassUid, contactUid, COLLECTED_CONTACT_EXISTS);
        cardDavServerExtension.setCreateCollectedContact(ALICE_OPENPAAS_USER_NAME, openPassUid, contactUid);

        // when alice sends an email to bob
        aliceSendAnEmailToBob();

        // then the endpoint createCollectedContact is not called
        cardDavServerExtension.assertCreateCollectedContactWasCalled(ALICE_OPENPAAS_USER_NAME, openPassUid, contactUid, 0);
    }

    @Test
    void shouldNotPUTCreateCollectedContactWhenSearchEmailDoesNotExist() throws Exception {
        // Setup mock server
        String contactUid = CardDavCreationFactory.createContactUid(BOB.asMailAddress());
        String openPassUid = UUID.randomUUID().toString();
        openPaasServerExtension.setSearchEmailNotFound(ALICE.asString());
        cardDavServerExtension.setCollectedContactExists(ALICE_OPENPAAS_USER_NAME, openPassUid, contactUid, !COLLECTED_CONTACT_EXISTS);
        cardDavServerExtension.setCreateCollectedContact(ALICE_OPENPAAS_USER_NAME, openPassUid, contactUid);

        // when alice sends an email to bob
        aliceSendAnEmailToBob();

        cardDavServerExtension.assertCollectedContactExistsWasCalled(ALICE_OPENPAAS_USER_NAME, openPassUid, contactUid, 0);
        cardDavServerExtension.assertCreateCollectedContactWasCalled(ALICE_OPENPAAS_USER_NAME, openPassUid, contactUid, 0);
    }

    @Test
    void shouldPUTCreateCollectedContactMultipleTimesWhenMultipleRecipients() throws Exception {
        // Setup mock server
        String bobContactUid = CardDavCreationFactory.createContactUid(BOB.asMailAddress());
        String cedricContactUid = CardDavCreationFactory.createContactUid(CEDRIC.asMailAddress());
        String aliceOpenPassId = UUID.randomUUID().toString();
        openPaasServerExtension.setSearchEmailExist(ALICE.asString(), aliceOpenPassId);
        cardDavServerExtension.setCollectedContactExists(ALICE_OPENPAAS_USER_NAME, aliceOpenPassId, bobContactUid, !COLLECTED_CONTACT_EXISTS);
        cardDavServerExtension.setCreateCollectedContact(ALICE_OPENPAAS_USER_NAME, aliceOpenPassId, bobContactUid);
        cardDavServerExtension.setCollectedContactExists(ALICE_OPENPAAS_USER_NAME, aliceOpenPassId, cedricContactUid, !COLLECTED_CONTACT_EXISTS);
        cardDavServerExtension.setCreateCollectedContact(ALICE_OPENPAAS_USER_NAME, aliceOpenPassId, cedricContactUid);

        // when alice sends an email to bob and cedric
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(ALICE.asString())
                    .addToRecipient(BOB.asString())
                    .addToRecipient(CEDRIC.asString())
                    .setSubject("Contact collection Rocks")
                    .setText("This is my email"))
                .sender(ALICE.asString())
                .recipients(BOB.asString(), CEDRIC.asString()));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(CEDRIC, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        // then the endpoint createCollectedContact is called twice
        cardDavServerExtension.assertCreateCollectedContactWasCalled(ALICE_OPENPAAS_USER_NAME, aliceOpenPassId, bobContactUid, 1);
        cardDavServerExtension.assertCreateCollectedContactWasCalled(ALICE_OPENPAAS_USER_NAME, aliceOpenPassId, cedricContactUid, 1);
    }

    @Test
    void shouldPUTCreateCollectedContactForContactDoesNotExistWhenMultipleRecipients() throws Exception {
        // Setup mock server
        String bobContactUid = CardDavCreationFactory.createContactUid(BOB.asMailAddress());
        String cedricContactUid = CardDavCreationFactory.createContactUid(CEDRIC.asMailAddress());
        String aliceOpenPassId = UUID.randomUUID().toString();
        openPaasServerExtension.setSearchEmailExist(ALICE.asString(), aliceOpenPassId);
        cardDavServerExtension.setCollectedContactExists(ALICE_OPENPAAS_USER_NAME, aliceOpenPassId, bobContactUid, COLLECTED_CONTACT_EXISTS);
        cardDavServerExtension.setCreateCollectedContact(ALICE_OPENPAAS_USER_NAME, aliceOpenPassId, bobContactUid);
        cardDavServerExtension.setCollectedContactExists(ALICE_OPENPAAS_USER_NAME, aliceOpenPassId, cedricContactUid, !COLLECTED_CONTACT_EXISTS);
        cardDavServerExtension.setCreateCollectedContact(ALICE_OPENPAAS_USER_NAME, aliceOpenPassId, cedricContactUid);

        // when alice sends an email to bob and cedric
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(ALICE.asString())
                    .addToRecipient(BOB.asString())
                    .addToRecipient(CEDRIC.asString())
                    .setSubject("Contact collection Rocks")
                    .setText("This is my email"))
                .sender(ALICE.asString())
                .recipients(BOB.asString(), CEDRIC.asString()));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(CEDRIC, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        // then the endpoint createCollectedContact is called once
        cardDavServerExtension.assertCreateCollectedContactWasCalled(ALICE_OPENPAAS_USER_NAME, aliceOpenPassId, bobContactUid, 0);
        cardDavServerExtension.assertCreateCollectedContactWasCalled(ALICE_OPENPAAS_USER_NAME, aliceOpenPassId, cedricContactUid, 1);
    }

    private void aliceSendAnEmailToBob() throws MessagingException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(ALICE.asString())
                    .addToRecipient(BOB.asString())
                    .setSubject("Contact collection Rocks")
                    .setText("This is my email"))
                .sender(ALICE.asString())
                .recipients(BOB.asString()));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }
}