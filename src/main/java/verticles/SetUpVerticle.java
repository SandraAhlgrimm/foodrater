package verticles;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;


/**
 * That's the first verticle that will be called, when the application starts.
 * It deploys the necessary verticles, to make them available to use via the vertx eventbus.
 * <p/>
 * Created by sandra.kriemann on 26.07.15.
 */

public class SetUpVerticle extends AbstractVerticle{

    @Override
    public void start() {
        vertx.deployVerticle("RestServerVerticle", new DeploymentOptions().setWorker(true));
    }
}
