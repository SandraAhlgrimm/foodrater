package verticles;

import io.vertx.core.json.JsonObject;
import com.mongodb.async.client.MongoClient;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;

/**
 * Handles the database connection and access the data out of the mongoDB.
 * </p>
 *
 * Created by sandra.kriemann on 26.07.15.
 */

public class MongoDBVerticle extends AbstractVerticle{

    @Override
    public void start() {
        final MongoClient mongo = MongoClient.createShared(vertx, new JsonObject().put("db_name", "demo"));

        EventBus eb = vertx.eventBus();
        MessageConsumer<String> consumer = eb.consumer(MongoDBVerticle.class.getSimpleName());
        consumer.handler(message -> {
            writeToMongoDB();
        });
    }

    private void writeToMongoDB() {

    }
}
