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

package org.apache.james.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.james.backends.redis.DockerRedis;
import org.apache.james.backends.redis.RedisExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.lettuce.core.Consumer;
import io.lettuce.core.KeyScanArgs;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import reactor.core.publisher.Mono;

@Disabled("Avoid CI build cost. This test suite is just for local dev experiment with Redis.")
class RedisPlaygroundTest {

    @RegisterExtension
    static RedisExtension redisExtension = new RedisExtension();

    @Nested
    class StringsTest {
        @Test
        void shouldOverrideValueWhenTheSameKey(DockerRedis redis) {
            RedisCommands<String, String> client = redis.createClient();
            String key = "KEY1";
            String keyValue = "Value1";
            client.set(key, keyValue);
            client.set(key, "overrideValue");

            assertThat(client.get(key)).isEqualTo("overrideValue");
        }

        @Test
        void getShouldReturnNullIfKeyDoesNotExist(DockerRedis redis) {
            RedisCommands<String, String> client = redis.createClient();
            String notExistedKey = "notExistedKey";

            assertThat(client.get(notExistedKey)).isNull();
        }

        @Test
        void getDelShouldResetValue(DockerRedis redis) {
            RedisCommands<String, String> client = redis.createClient();
            String key = "KEY1";
            String keyValue = "Value1";
            client.set(key, keyValue);

            client.getdel(key);

            assertThat(client.get(key)).isNull();
        }
    }

    @Nested
    class SetsTest {
        @Test
        void addMembersToASet(DockerRedis redis) {
            RedisCommands<String, String> client = redis.createClient();
            String key = "KEY1";
            String value1 = "Value1";
            String value2 = "Value2";
            client.sadd(key, value1, value2);

            assertThat(client.smembers(key)).containsExactlyInAnyOrder(value1, value2);
        }

        @Test
        void reAddExistingElementToASetShouldNotFailAndNotDuplicateTheElement(DockerRedis redis) {
            RedisCommands<String, String> client = redis.createClient();
            String key = "KEY1";
            String value1 = "Value1";
            client.sadd(key, value1);

            client.sadd(key, value1);

            assertThat(client.smembers(key)).containsOnly(value1);
        }

        @Test
        void getANonExistingSetShouldReturnEmpty(DockerRedis redis) {
            RedisCommands<String, String> client = redis.createClient();
            String nonExistingKey = "KEY1";

            assertThat(client.smembers(nonExistingKey)).isEmpty();
        }

        @Test
        void deleteAnElementOutOfSet(DockerRedis redis) {
            RedisCommands<String, String> client = redis.createClient();
            String key = "KEY1";
            String value1 = "Value1";
            String value2 = "Value2";
            client.sadd(key, value1, value2);

            client.srem(key, value1);

            assertThat(client.smembers(key)).containsOnly(value2);
        }

        @Test
        void deleteAllElementsOutOfSetShouldMakeTheSetEmpty(DockerRedis redis) {
            RedisCommands<String, String> client = redis.createClient();
            String key = "KEY1";
            String value1 = "Value1";
            String value2 = "Value2";
            client.sadd(key, value1, value2);

            client.srem(key, value1);
            client.srem(key, value2);

            assertThat(client.smembers(key)).isEmpty();
        }


        @Test
        void cleanMappingsToInactiveChannels(DockerRedis redis) {
            RedisCommands<String, String> client = redis.createClient();

            // assume having active channels using PUBSUB CHANNELS command
            String activeChannel1 = "mailboxEvent-eventbus-178c5bf9-67ce-4013-9500-f4be557260a8";
            String activeChannel2 = "emailAddressContactEvent-eventbus-33e7bbc4-715d-4bd2-a04d-fcb251721ccf";
            String inactiveChannel = "jmapEvent-eventbus-eba72423-26be-4275-ab0a-88b59e790826";
            List<String> activeChannels = List.of(activeChannel1, activeChannel2);

            // provision some event bus mappings
            String nonRelatedSetKey = "nonRelatedSetKey";
            String routingKey1 = "mailboxId1";
            client.sadd(nonRelatedSetKey, "whateverValue");
            client.sadd(routingKey1, activeChannel1, activeChannel2, inactiveChannel);

            // before clean up
            assertThat(client.smembers(routingKey1)).contains(inactiveChannel);

            // clean up
            KeyScanArgs scanRedisSetKeys = KeyScanArgs.Builder
                .type("set");
            List<String> setKeys = client.scan(ScanCursor.INITIAL, scanRedisSetKeys)
                .getKeys();

            setKeys.forEach(key -> {
                if (isEventBusKey(key)) {
                    client.smembers(key).forEach(channel -> {
                        if (!activeChannels.contains(channel)) {
                            client.srem(key, channel);
                        }
                    });
                }
            });

            // after clean up mapping to the inactive channel
            assertThat(client.smembers(routingKey1)).doesNotContain(inactiveChannel);
        }

        private boolean isEventBusKey(String value) {
            // in reality we could use RoutingKeyConverter::toRegistrationKey to know if it is a RegistrationKey.
            return value.contains("mailboxId");
        }
    }

    @Nested
    class RedisStreams {
        @Test
        void consumerGroupTest(DockerRedis redis) {
            RedisCommands<String, String> redisCommands = redis.createClient();

            String stream = "weather_sensor:wind";

            // create consumer group
            String consumerGroup = "jamesConsumers";
            redisCommands.xgroupCreate(XReadArgs.StreamOffset.from(stream, "0-0"), consumerGroup,
                XGroupCreateArgs.Builder.mkstream());

            // register 2 consumers to the stream
            publishMessageToRedisStream(redisCommands, stream);
            StreamMessage<String, String> messageByConsumer1 = redisCommands.xreadgroup(
                    Consumer.from(consumerGroup, "consumer_1"),
                    XReadArgs.StreamOffset.lastConsumed(stream))
                .get(0);
            System.out.println("Consumer is consuming message with id " + messageByConsumer1.getId());

            publishMessageToRedisStream(redisCommands, stream);
            StreamMessage<String, String> messageByConsumer2 = redisCommands.xreadgroup(
                    Consumer.from(consumerGroup, "consumer_2"),
                    XReadArgs.StreamOffset.lastConsumed(stream))
                .get(0);
            System.out.println("Consumer 2 is consuming message with id " + messageByConsumer2.getId());

            assertThat(messageByConsumer1.getId()).isNotEqualTo(messageByConsumer2.getId())
                .as("Consumer 2 does not consume the same message with Consumer 1");
        }

        @Test
        void ackedMessageTest(DockerRedis redis) {
            RedisCommands<String, String> redisCommands = redis.createClient();

            String stream = "weather_sensor:wind";

            // create consumer group
            String consumerGroup = "jamesConsumers";
            redisCommands.xgroupCreate(XReadArgs.StreamOffset.from(stream, "0-0"), consumerGroup,
                XGroupCreateArgs.Builder.mkstream());

            // GIVEN the consumer 1 does not acked the message after processing it e.g. because of failure
            publishMessageToRedisStream(redisCommands, stream);
            StreamMessage<String, String> messageByConsumer1 = redisCommands.xreadgroup(
                    Consumer.from(consumerGroup, "consumer_1"),
                    XReadArgs.StreamOffset.from(stream, ">"))
                .get(0);
            System.out.println("Consumer 1 failed to consume message with id " + messageByConsumer1.getId());

            // The consumer 1 can not see the unacked message using offset > (only return new and not unacked messages)
            assertThat(redisCommands.xreadgroup(Consumer.from(consumerGroup, "consumer_1"), XReadArgs.StreamOffset.from(stream, ">")))
                .isEmpty();

            // Other consumers can not see the unacked message even using offset 0 (because the unacked message is only visible to consumer 1 which tried to consume it)
            assertThat(redisCommands.xreadgroup(Consumer.from(consumerGroup, "consumer_2"), XReadArgs.StreamOffset.from(stream, "0")))
                .isEmpty();

            // THEN the consumer 1 can re-processing the unacked message using offset 0
            StreamMessage<String, String> messageReprocessingByConsumer1 = redisCommands.xreadgroup(
                    Consumer.from(consumerGroup, "consumer_1"),
                    XReadArgs.StreamOffset.from(stream, "0"))
                .get(0);
            assertThat(messageReprocessingByConsumer1.getId()).isEqualTo(messageByConsumer1.getId());
            // Confirm that the message has been processed using XACK
            redisCommands.xack(stream, consumerGroup, messageReprocessingByConsumer1.getId());
            System.out.println("Consumer 1 succeeded to re-consume message with id " + messageReprocessingByConsumer1.getId());

            // There should be no unacked message now
            assertThat(redisCommands.xpending(stream, consumerGroup)
                .getCount())
                .isZero();
        }

        @Test
        void nonConsumerGroupTest(DockerRedis redis) {
            // Goal: Test XREAD command (messages should be broadcast to all consumers)
            RedisCommands<String, String> redisCommands = redis.createClient();

            String stream = "weather_sensor:wind";

            // publish a message to a Redis Stream
            publishMessageToRedisStream(redisCommands, stream);

            // Consumer1 consumes the message 1st
            StreamMessage<String, String> messageByConsumer1 = redisCommands.xread(XReadArgs.StreamOffset.from(stream, "0"))
                .get(0);
            System.out.println("Consumer is consuming message with id " + messageByConsumer1.getId());

            // Assume consumer1 restarts and consume the message again from offset 0 (start point of all messages)
            StreamMessage<String, String> messageByConsumer1Again = redisCommands.xread(XReadArgs.StreamOffset.from(stream, "0"))
                .get(0);
            System.out.println("Consumer is consuming message with id " + messageByConsumer1Again.getId());

            assertThat(messageByConsumer1.getId()).isEqualTo(messageByConsumer1Again.getId())
                .as("Because XREAD does not support ACK message like XREADGROUP, consumer can keep consuming the same message upon restart. " +
                    "Therefore consumers needs to manage the (last consumed) message offset itself. Seems not convenient for the key registration case..." +
                    "Redis Pub/sub maybe more suitable for key registration case.");
        }
    }

    @Nested
    class RedisPubSub {

        class RedisPubSubListener extends RedisPubSubAdapter<String, String> {
            private final int consumerId;
            private final int listenerId;
            private final CountDownLatch latch;

            RedisPubSubListener(int consumerId, int listenerId, CountDownLatch latch) {
                this.consumerId = consumerId;
                this.listenerId = listenerId;
                this.latch = latch;
            }

            @Override
            public void message(String channel, String message) {
                System.out.println("Listener " + listenerId + " of Consumer " + consumerId + " received from channel " + channel + " message: " + message);
                latch.countDown();
            }
        }

        @Test
        void pubSubShouldPublishMessageToAllConsumers(DockerRedis redis) throws InterruptedException {
            String channel = "testChannel";
            String message = "Hello, Lettuce Redis Pub/Sub!";

            // Set up a latch to wait for the message
            CountDownLatch latch = new CountDownLatch(2);

            // create 2 consumers to the Redis Pub/Sub channel, each consumer has 2 listeners
            StatefulRedisPubSubConnection<String, String> consumer1Connection = createRawRedisClient(redis).connectPubSub();
            consumer1Connection.addListener(new RedisPubSubListener(1, 1, latch));
            consumer1Connection.sync().subscribe(channel);

            StatefulRedisPubSubConnection<String, String> consumer2Connection = createRawRedisClient(redis).connectPubSub();
            consumer2Connection.addListener(new RedisPubSubListener(2, 1, latch));
            consumer2Connection.sync().subscribe(channel);

            // Publish a message to the channel
            RedisPubSubCommands<String, String> publisher = createRawRedisClient(redis).connectPubSub().sync();
            publisher.publish(channel, message);

            // Wait for the latch to count down, indicating that the message has been received by 2 consumer
            latch.await();

            // Assert that the message was received
            assertEquals(0, latch.getCount(), "Message was not fully received");
        }

        @Test
        @Disabled("Redis Pub/sub test upon Redis standalone restart. Please configure DockerRedis to expose to a static port on the host machine e.g:" +
            "redisContainer.setPortBindings(List.of(String.format(%d:%d, 6379, 6379)))")
        void pubSubMessagesShouldBeWellReceivedAfterRedisStandaloneRestart(DockerRedis redis) throws InterruptedException {
            String channel = "testChannel";
            String message = "Hello, Lettuce Redis Pub/Sub!";

            // Set up a latch to wait for the message
            CountDownLatch latch = new CountDownLatch(2);

            // create the Pub/sub publisher and subscriber connection beforehand (just like RedisEventBus)
            StatefulRedisPubSubConnection<String, String> subscriberConnection = createRawRedisClient(redis).connectPubSub();
            RedisPubSubCommands<String, String> publisher = createRawRedisClient(redis).connectPubSub().sync();

            // Consumer 1 subscribes the Listener 1 to the Redis Pub/Sub channel, before the Redis restart
            subscriberConnection.sync().subscribe(channel);
            subscriberConnection.addListener(new RedisPubSubListener(1, 1, latch));

            // Redis restarts in the middle of pub sub
            redis.stop();
            Thread.sleep(2000L); // simulate a downtime
            redis.start();

            // Await a bit for the Redis server to be fully started, and Redis Pub/sub consumer can reconnect
            Thread.sleep(2000L);

            // Consumer 1 subscribes the Listener 2 to the Redis Pub/Sub channel, after Redis restart, on the existing Lettuce Pub/sub connection.
            subscriberConnection.addListener(new RedisPubSubListener(1, 2, latch));

            // Publish a message to the channel
            publisher.publish(channel, message);

            // Two listeners should receive 2 messages in total well
            latch.await(10L, TimeUnit.SECONDS);
            assertEquals(0, latch.getCount(), "Messages were fully received");
        }

        @Test
        void allListenersShouldBeNotifiedAboutTheMessage(DockerRedis redis) throws InterruptedException {
            String channel = "testChannel";
            String message = "Hello, Lettuce Redis Pub/Sub!";

            // Set up a latch to wait for the message
            CountDownLatch latch = new CountDownLatch(4);

            // create 2 consumers to the Redis Pub/Sub channel, each consumer have 2 attached listeners (to handle the message)
            StatefulRedisPubSubConnection<String, String> consumer1Connection = createRawRedisClient(redis).connectPubSub();
            consumer1Connection.addListener(new RedisPubSubListener(1, 1, latch));
            consumer1Connection.addListener(new RedisPubSubListener(1, 2, latch));
            consumer1Connection.sync().subscribe(channel);

            StatefulRedisPubSubConnection<String, String> consumer2Connection = createRawRedisClient(redis).connectPubSub();
            consumer2Connection.addListener(new RedisPubSubListener(2, 1, latch));
            consumer2Connection.addListener(new RedisPubSubListener(2, 2, latch));
            consumer2Connection.sync().subscribe(channel);

            // Publish a message to the channel
            RedisPubSubCommands<String, String> publisher = createRawRedisClient(redis).connectPubSub().sync();
            publisher.publish(channel, message);

            // Wait for the latch to count down, indicating that the message has been received by 2 consumer
            latch.await();

            // Assert that the message was received
            assertEquals(0, latch.getCount(), "Message was not fully received");
        }

        @Test
        void pubSubShouldNotPublishMessageUnsubscribedConsumer(DockerRedis redis) throws InterruptedException {
            String channel = "testChannel";
            String message = "Hello, Lettuce Redis Pub/Sub!";

            // Set up a latch to wait for the message
            CountDownLatch latch = new CountDownLatch(2);

            // create 2 consumers to the Redis Pub/Sub channel, each consumer has 2 listeners
            StatefulRedisPubSubConnection<String, String> consumer1Connection = createRawRedisClient(redis).connectPubSub();
            consumer1Connection.addListener(new RedisPubSubListener(1, 1, latch));
            consumer1Connection.sync().subscribe(channel);

            StatefulRedisPubSubConnection<String, String> consumer2Connection = createRawRedisClient(redis).connectPubSub();
            consumer2Connection.addListener(new RedisPubSubListener(2, 1, latch));
            consumer2Connection.sync().subscribe(channel);

            // consumer 2 unsubscribes the channel
            consumer2Connection.sync().unsubscribe(channel);

            // Publish a message to the channel
            RedisPubSubCommands<String, String> publisher = createRawRedisClient(redis).connectPubSub().sync();
            publisher.publish(channel, message);

            // Wait for the latch to count down
            latch.await(3, TimeUnit.SECONDS);

            // Assert that the message was received only by consumer 1
            assertThat(latch.getCount()).isEqualTo(1);
        }

        @Test
        void laterSubscribeWouldNotSeeThePublishedMessage(DockerRedis redis) throws InterruptedException {
            String channel = "testChannel";
            String message = "Hello, Lettuce Redis Pub/Sub!";

            // Set up a latch to wait for the message
            CountDownLatch latch = new CountDownLatch(2);

            // Consumer 1 subscribes to the Redis Pub/Sub channel
            StatefulRedisPubSubConnection<String, String> consumer1Connection = createRawRedisClient(redis).connectPubSub();
            consumer1Connection.addListener(new RedisPubSubListener(1, 1, latch));
            consumer1Connection.sync().subscribe(channel);

            // Publish a message to the channel
            RedisPubSubCommands<String, String> publisher = createRawRedisClient(redis).connectPubSub().sync();
            publisher.publish(channel, message);

            // Later... consumer 2 subscribes to the Redis Pub/Sub channel
            StatefulRedisPubSubConnection<String, String> consumer2Connection = createRawRedisClient(redis).connectPubSub();
            consumer2Connection.addListener(new RedisPubSubListener(2, 1, latch));
            consumer2Connection.sync().subscribe(channel);

            // Wait for the latch to count down
            latch.await(3, TimeUnit.SECONDS);

            // The message has been received by only consumer 1. Consumer 2 does not connect / subscribe to the channel when the message is being fired and forget, therefore it does not receive the message.
            assertThat(latch.getCount()).isEqualTo(1);
        }

        @Test
        void laterSubscribeWouldNotSeeThePublishedMessageReactive(DockerRedis redis) throws InterruptedException {
            String channel = "testChannel100";
            String message = "Hello, Lettuce Redis Pub/Sub!";

            // Set up a latch to wait for the message
            CountDownLatch latch = new CountDownLatch(2);

            // Consumer 1 subscribes to the Redis Pub/Sub channel
            StatefulRedisPubSubConnection<String, String> consumer1Connection = createRawRedisClient(redis).connectPubSub();
            consumer1Connection.addListener(new RedisPubSubListener(1, 1, latch));
            consumer1Connection.reactive().subscribe(channel).block();

            // Publish a message to the channel
            RedisPubSubReactiveCommands<String, String> publisher = createRawRedisClient(redis).connectPubSub().reactive();
            Mono<Long> receivedConsumersCountMono = publisher.publish(channel, message);
            Long receivedConsumersCount = receivedConsumersCountMono.block();

            // Later... consumer 2 subscribes to the Redis Pub/Sub channel
            StatefulRedisPubSubConnection<String, String> consumer2Connection = createRawRedisClient(redis).connectPubSub();
            consumer2Connection.addListener(new RedisPubSubListener(2, 1, latch));
            consumer2Connection.reactive().subscribe(channel).block();

            // Wait for the latch to count down
            latch.await(3, TimeUnit.SECONDS);

            // The message has been received by only consumer 1. Consumer 2 does not connect / subscribe to the channel when the message is being fired and forget, therefore it does not receive the message.
            assertThat(receivedConsumersCount).isEqualTo(1L);
        }

        @Test
        void publishMessageToANonExistingChannelShouldNotFail(DockerRedis redis) {
            String nonExistingChannel = "testChannel1000";
            String message = "Hello, Lettuce Redis Pub/Sub!";

            Long consumersReceivedCount = createRawRedisClient(redis).connectPubSub().reactive()
                .publish(nonExistingChannel, message)
                .block();

            assertThat(consumersReceivedCount).isEqualTo(0);
        }
    }

    private void publishMessageToRedisStream(RedisCommands<String, String> redisCommands, String redisStream) {
        Map<String, String> messageBody = new HashMap<>();
        messageBody.put( "speed", "15" );
        messageBody.put( "direction", "270" );
        messageBody.put( "sensor_ts", String.valueOf(System.currentTimeMillis()));
        String messageId = redisCommands.xadd(redisStream, messageBody);
        System.out.println(String.format("Message with id %s : %s published to Redis Streams", messageId, messageBody));
    }

    private RedisClient createRawRedisClient(DockerRedis redis) {
        return RedisClient.create(redis.redisURI().toString());
    }
}
