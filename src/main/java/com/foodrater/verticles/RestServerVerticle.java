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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Controlles the REST Services.
 * <p>
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
        router.get("/products/search/:word").handler(this::searchProduct);
        router.put("/products/:productID").handler(this::handleAddProduct);
        router.get("/products").handler(this::handleListProducts);
        router.get("/initialize").handler(this::setUpInitialData);
        router.get("/myproducts/:UUID").handler(this::getAllProductsForUser);
        router.get("/users/:userID").handler(this::getUserInformation);
        router.get("/user/login/:username/:pw").handler(this::getUserLogin);
        router.put("/user/register").handler(this::handleAddUser);

        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
    }

    private void getUserLogin(RoutingContext routingContext) {
        String userName = routingContext.request().getParam("username");
        String pw = routingContext.request().getParam("pw");
        HttpServerResponse response = routingContext.response();

        if (userName.equals(null) || pw.equals(null) || userName.length() < 1 || pw.length() < 1) {
            sendError(400, response);
        } else {
            try {
                if (findUserInMongoDB(userName, pw) != null) {
                    response.putHeader("content-type", "application/json").end((findUserInMongoDB(userName, pw)).encodePrettily());
                } else {
                    sendError(400, response);
                }
            } catch (InterruptedException e) {
                LOGGER.error("Couldn't find User.");
                sendError(400, response);
            }
        }
    }

    private JsonObject findUserInMongoDB(String userName, String pw) throws InterruptedException {
        JsonObject resultedUser = new JsonObject();
        JsonObject query = new JsonObject();
        query.put("userName", userName);
        query.put("pw", pw);
        CountDownLatch latch = new CountDownLatch(1);
        mongo.find("users.user", query, res -> {
            if (res.succeeded()) {
                for (JsonObject json : res.result()) {
                    LOGGER.info("Found user:" + json.encodePrettily());
                    resultedUser.put(json.getString("uuid"), json);
                    LOGGER.info("Result Json:" + resultedUser.encodePrettily());
                }
            }
            latch.countDown();
        });
        latch.await();

        LOGGER.info("Final result Json:" + resultedUser.encodePrettily());
        return resultedUser;
    }

    private void handleAddUser(RoutingContext routingContext) {
        // add check, if user already exists
        HttpServerResponse response = routingContext.response();
        JsonObject user = null;
        try {
            user = routingContext.getBodyAsJson();
        } catch (Exception e) {
            sendError(400, response);
        }
        if (user == null) {
            sendError(400, response);
        } else {
            UUID uuid = new UUID(10000L, 100L);
            JsonObject newUser = new JsonObject();
            newUser.put("UUID", uuid.toString());
            newUser.put("user", user);
            insertUserInMongo(newUser);
            response.putHeader("content-type", "application/json").end(newUser.encodePrettily());
        }
    }


    private void insertUserInMongo(JsonObject user) {
        mongo.insert("users", user, stringAsyncResult -> {
            if (stringAsyncResult.succeeded()) {
                LOGGER.info("Inserted user into mongoDB: " + user.encodePrettily());
            } else {
                LOGGER.error("Could not insert user into mongoDB: " + user.encodePrettily());
            }
        });
    }

    private void getUserInformation(RoutingContext routingContext) {
        String uuid = routingContext.request().getParam("uuid");
        HttpServerResponse response = routingContext.response();
        JsonObject query = new JsonObject();
        query.put("UUID", uuid);
        mongo.find("users", query, res -> {
            if (res.succeeded()) {
                for (JsonObject json : res.result()) {
                    LOGGER.info("Found user:" + json.encodePrettily());
                    response.putHeader("content-type", "application/json").end(json.encodePrettily());
                }
            } else {
                sendError(400, response);
            }
        });
    }

    /**
     * response all products for user as json ->  {prodId:{productjson1}, {productjson2}, ... }
     *
     * @param routingContext incoming RotingContext with param UUID
     */
    private void getAllProductsForUser(RoutingContext routingContext) {
        String uuid = routingContext.request().getParam("UUID");
        JsonObject query = new JsonObject().put("UUID", uuid);
        HttpServerResponse response = routingContext.response();
        JsonObject allProducts = new JsonObject();

        mongo.find("users", query, res -> {
            if (res.succeeded()) {
                for (JsonObject userJson : res.result()) {
                    JsonObject query2 = new JsonObject().put("prodID", userJson.getString("prodID"));
                    mongo.find("products", query2, res2 -> {
                        if (res2.succeeded()) {
                            for (JsonObject product : res2.result()) {
                                allProducts.put(userJson.getString("prodID"), product);
                            }
                        }
                    });
                }
                response.putHeader("content-type", "application/json").end(allProducts.encodePrettily());
            } else {
                sendError(400, response);
            }
        });
    }

    /**
     * response all products with name inside as json
     * { {product1}, {product2}, {product3}.. }
     *
     * @param routingContext incoming RotingContext with param search-word
     */
    private void searchProduct(RoutingContext routingContext) {
        String word = routingContext.request().getParam("word");
        JsonObject query = new JsonObject().put("name", "/" + word + "/");
        //LOGGER.info("Search in mongodb for this query: " + query.encodePrettily());
        HttpServerResponse response = routingContext.response();
        JsonObject fittingProducts = new JsonObject();
        CountDownLatch latch = new CountDownLatch(1);
        mongo.find("products", query, res -> {
            if (res.succeeded()) {
                for (JsonObject foundedProduct : res.result()) {
                    LOGGER.info("Found Product with search: " + foundedProduct.encodePrettily());
                    fittingProducts.put(foundedProduct.getJsonObject("product").getString("id"), foundedProduct);
                    latch.countDown();
                }
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    LOGGER.error("Couldn't find any with: " + word);
                    sendError(400, response);
                }
                LOGGER.info("Reached that point" + fittingProducts.encodePrettily());
                response.putHeader("content-type", "application/json").end(fittingProducts.encodePrettily());
            }
        });
    }

    private void setUpInitialData(RoutingContext routingContext) {
        addProduct(new JsonObject().put("prodID", "prod3568").put("name", "Egg Whisk").put("price", 3.99).put("weight", 150));
        addProduct(new JsonObject().put("prodID", "prod7340").put("name", "Tea Cosy").put("price", 5.99).put("weight", 100));
        addProduct(new JsonObject().put("prodID", "prod8643").put("name", "Spatula").put("price", 1.00).put("weight", 80));
        insertUserInMongo(new JsonObject().put("uuid", (new UUID(10L, 1000L)).toString()).put("userName", "Sebastian").put("pw", "123abc"));
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
        query.put(productID + ".prodID", productID);
        JsonObject result = new JsonObject();
        CountDownLatch latch = new CountDownLatch(1);

        LOGGER.info("Trying to find " + query.encodePrettily());
        mongo.find("products", query, res -> {
            if (res.succeeded()) {
                for (JsonObject json : res.result()) {
                    LOGGER.info("Found product:" + json.encodePrettily());
                    result.put("product", json);
                    LOGGER.info("Result Json:" + result.encodePrettily());
                }
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            LOGGER.error("latch error: " + e.getMessage());
        }

        LOGGER.info("Final result Json:" + result.encodePrettily());
        return result;
    }

    /**
     * @param routingContext json{uuid: '00-kP', prodID: '426234', rating: '1', lat: '34.65',
     *                       lng: '-23.523'storeName: 'REWE' }
     */
    private void handleAddProduct(RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        try {
            JsonObject voting = routingContext.getBodyAsJson();
            JsonObject query = new JsonObject().put("UUID", voting.getString("uuid"));
            mongo.find("users", query, res -> {
                if (res.succeeded()) {
                    for (JsonObject user : res.result()) {
                        user.put(voting.getString("prodID"), voting);
                        mongo.insert("user", user, res2 -> {
                            if (res2.succeeded()) {
                                LOGGER.info("Updatet user with voting: " + voting);
                                response.end();
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            sendError(400, response);
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
        products.put(product.getString("prodID"), product);
        JsonObject jsonObject = new JsonObject();
        jsonObject.put(product.getString("prodID"), product);
        LOGGER.info("JsonObject to insert: " + jsonObject.encodePrettily());
        insertInMongo(jsonObject);
    }
}
