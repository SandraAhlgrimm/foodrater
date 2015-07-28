package com.foodrater.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Controlles the REST Services.
 *
 * Created by sandra.kriemann on 26.07.15.
 * https://github.com/SandraKriemann/vertx-examples/blob/master/web-examples/src/main/java/io/vertx/example/web/rest/SimpleREST.java
 */
public class RestServerVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestServerVerticle.class.getSimpleName());

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
        LOGGER.info("MongoClient is started with this config: " + new JsonObject(DEFAULT_MONGODB_CONFIG).encodePrettily());
        //setUpInitialData();

        Router router = Router.router(vertx);

        // Needs to be added if you want to access your frontend on the same server
        CorsHandler corsHandler = CorsHandler.create("*");
        corsHandler.allowedMethod(HttpMethod.GET);
        corsHandler.allowedMethod(HttpMethod.POST);
        corsHandler.allowedMethod(HttpMethod.PUT);
        corsHandler.allowedMethod(HttpMethod.DELETE);
        corsHandler.allowedHeader("Authorization");
        corsHandler.allowedHeader("Content-Type");
        corsHandler.allowedHeader("Access-Control-Allow-Origin");
        corsHandler.allowedHeader("Access-Control-Allow-Headers");
        router.route().handler(corsHandler);

        router.route().handler(BodyHandler.create());
        router.get("/products/:productID").handler(this::handleGetProduct);
        router.get("/products/search/:words").handler(this::searchProduct);
        router.put("/products/:productID").handler(this::handleAddProduct);
        router.get("/products").handler(this::handleListProducts);
        router.get("/initialize").handler(this::setUpInitialData);
        router.get("/myproducts/:userID").handler(this::getAllProductsForUser);
        router.get("/user/:userID").handler(this::getUserInformation);

        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
    }

    private void getUserInformation(RoutingContext routingContext) {
        // return all user info
    }

    private void getAllProductsForUser(RoutingContext routingContext) {
        // return all products for user as json ->  {prodId:{productjson1}, {productjson2}, ... }
    }

    private void searchProduct(RoutingContext routingContext) {
        // return all products with name inside as json
        // { {product1}, {product2}, {product3}.. }
    }

    private void setUpInitialData(RoutingContext routingContext) {
        addProduct(new JsonObject().put("id", "prod3568").put("name", "Egg Whisk").put("price", 3.99).put("weight", 150));
        addProduct(new JsonObject().put("id", "prod7340").put("name", "Tea Cosy").put("price", 5.99).put("weight", 100));
        addProduct(new JsonObject().put("id", "prod8643").put("name", "Spatula").put("price", 1.00).put("weight", 80));
        // + average rating and amount of ratings
        HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "application/json").end("initialized");
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
                response.putHeader("content-type", "application/json").end(product.encode());
            }
        }
    }

    private JsonObject findProductInMongoDB(String productID) {
        JsonObject query = new JsonObject();
        query.put(productID + ".id", productID);
        JsonObject result = new JsonObject();
        CountDownLatch latch = new CountDownLatch(1);


        LOGGER.info("Trying to find " + query.encodePrettily());
        mongo.find("products", query, res -> {
            if (res.succeeded()) {
                for (JsonObject json : res.result()) {
                    LOGGER.info("Found product:" + json.encodePrettily());
                    result.put(productID, json);
                    LOGGER.info("Result Json:" + result.encodePrettily());
                }
            }
            latch.countDown();

        });
        LOGGER.info("Final result Json:" + result.encodePrettily());
        try {
            latch.await();
        } catch (InterruptedException e) {
            LOGGER.error("latch error: " + e.getMessage());
        }
        return result;
    }

    private void handleAddProduct(RoutingContext routingContext) {
        String productID = routingContext.request().getParam("productID");
        HttpServerResponse response = routingContext.response();
        if (productID == null) {
            sendError(400, response);
        } else {
            JsonObject product = routingContext.getBodyAsJson(); // change product to user
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
        // calculate average rating + update product database averageRating + update user database add userproducts : {productId : , userRating : }
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

    private void addProduct(JsonObject product) {
        products.put(product.getString("id"), product);
        JsonObject jsonObject = new JsonObject();
        jsonObject.put(product.getString("id"), product);
        LOGGER.info("JsonObject to insert: " + jsonObject.encodePrettily());
        insertInMongo(jsonObject);
    }
}
