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

package com.linagora.tmail.encrypted.postgres;

import java.io.ByteArrayInputStream;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.google.common.io.BaseEncoding;
import com.linagora.tmail.encrypted.KeyId;
import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.PublicKey;
import com.linagora.tmail.pgp.Encrypter;

import reactor.core.publisher.Mono;

public class PostgresKeystoreManager implements KeystoreManager {
    private final PostgresKeystoreDAO.Factory keystoreDAOFactory;

    @Inject
    public PostgresKeystoreManager(PostgresKeystoreDAO.Factory keystoreDAOFactory) {
        this.keystoreDAOFactory = keystoreDAOFactory;
    }

    @Override
    public Publisher<KeyId> save(Username username, byte[] payload) {
        return Mono.fromCallable(() -> {
            try {
                return computeKeyId(payload);
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        }).flatMap(keyId -> keystoreDAOFactory.create(username.getDomainPart()).insertKey(username, new PublicKey(keyId, payload))
            .thenReturn(new KeyId(keyId)));
    }

    @Override
    public Publisher<PublicKey> listPublicKeys(Username username) {
        return keystoreDAOFactory.create(username.getDomainPart()).getAllKeys(username);
    }

    @Override
    public Publisher<PublicKey> retrieveKey(Username username, String id) {
        return keystoreDAOFactory.create(username.getDomainPart()).getKey(username, id);
    }

    @Override
    public Publisher<Void> delete(Username username, String id) {
        return keystoreDAOFactory.create(username.getDomainPart()).deleteKey(username, id);
    }

    @Override
    public Publisher<Void> deleteAll(Username username) {
        return keystoreDAOFactory.create(username.getDomainPart()).deleteAllKeys(username);
    }

    private String computeKeyId(byte[] payload) throws Exception {
        return BaseEncoding.base16()
            .encode(Encrypter.readPublicKey(new ByteArrayInputStream(payload))
                .getFingerprint());
    }
}
