package com.foodrater.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlles the REST Services.
 *
 * Created by sandra.kriemann on 26.07.15.
 * https://github.com/SandraKriemann/vertx-examples/blob/master/web-examples/src/main/java/io/vertx/example/web/rest/SimpleREST.java
 */
public class RestServerVerticle extends AbstractVerticle {

    private Map<String, JsonObject> products = new HashMap<>();

    public static final String ADDRESS = "mongodb-persistor";
    public static final String DEFAULT_MONGODB_CONFIG
            = "{"
            + "    \"address\": \"" + ADDRESS + "\","
            + "    \"host\": \"localhost\","
            + "    \"port\": 27017,"
            + "    \"db_name\": \"bs\","
            + "    \"useObjectId\" : true"
            + "}";
    MongoClient mongo;



    @Override
    public void start() {
        mongo = MongoClient.createShared(vertx, new JsonObject(DEFAULT_MONGODB_CONFIG));

        setUpInitialData();

        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());
        router.get("/products/:productID").handler(this::handleGetProduct);
        router.put("/products/:productID").handler(this::handleAddProduct);
        router.get("/products").handler(this::handleListProducts);

        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
    }

    private void handleGetProduct(RoutingContext routingContext) {
        String productID = routingContext.request().getParam("productID");
        HttpServerResponse response = routingContext.response();
        if (productID == null) {
            sendError(400, response);
        } else {
            //JsonObject product = products.get(productID);
            JsonObject product = findProductInMongoDB(productID);
            if (product == null) {
                sendError(404, response);
            } else {
                response.putHeader("content-type", "application/json").end(product.encodePrettily());
            }
        }
    }

    private JsonObject findProductInMongoDB(String productID) {
        JsonObject query = new JsonObject();
        query.put("productID", productID);
        JsonObject result = new JsonObject();

        mongo.find("products", query, res -> {
            if (res.succeeded()) {
                for (JsonObject json : res.result()) {
                    result.put(productID, json);
                }
            }
        });
        return result;
    }

    private void handleAddProduct(RoutingContext routingContext) {
        String productID = routingContext.request().getParam("productID");
        HttpServerResponse response = routingContext.response();
        if (productID == null) {
            sendError(400, response);
        } else {
            JsonObject product = routingContext.getBodyAsJson();
            if (product == null) {
                sendError(400, response);
            } else {
                products.put(productID, product);
                JsonObject productAsJson = new JsonObject();
                productAsJson.put(productID, product);
                insertInMongo(productAsJson);
                response.end();
            }
        }
    }

    private void insertInMongo(JsonObject productAsJson) {
        mongo.insert(("products"), productAsJson, res -> {

            if (res.succeeded()) {
                String id = res.result();

            } else {
                res.cause().printStackTrace();
            }
        });

    }

    private void handleListProducts(RoutingContext routingContext) {
        JsonArray arr = new JsonArray();
        products.forEach((k, v) -> arr.add(v));
        routingContext.response().putHeader("content-type", "application/json").end(arr.encodePrettily());
    }

    private void sendError(int statusCode, HttpServerResponse response) {
        response.setStatusCode(statusCode).end();
    }

    private void setUpInitialData() {
        addProduct(new JsonObject().put("id", "prod3568").put("name", "Egg Whisk").put("price", 3.99).put("weight", 150));
        addProduct(new JsonObject().put("id", "prod7340").put("name", "Tea Cosy").put("price", 5.99).put("weight", 100));
        addProduct(new JsonObject().put("id", "prod8643").put("name", "Spatula").put("price", 1.00).put("weight", 80));
    }

    private void addProduct(JsonObject product) {
        products.put(product.getString("id"), product);
        JsonObject jsonObject = new JsonObject();
        jsonObject.put(product.getString("id"), product);
        insertInMongo(jsonObject);
    }
}
