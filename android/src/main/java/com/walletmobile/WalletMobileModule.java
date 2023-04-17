package com.walletmobile;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;

@ReactModule(name = WalletMobileModule.NAME)
public class WalletMobileModule extends ReactContextBaseJavaModule {
  public static final String NAME = "WalletMobile";

  public WalletMobileModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }


  // Example method
  // See https://reactnative.dev/docs/native-modules-android
  @ReactMethod
  public void multiply(double a, double b, Promise promise) {
    promise.resolve(a * b);
  }


  Wallet wallet = null;

  @ReactMethod
  public void messageHandlerNew(
    ReadableMap clientOptions,
    String storagePath,
    Integer coinType,
    ReadableMap secretManager
  ) throws JSONException {

      String path = getContext().getFilesDir() + storagePath;
      Log.d("clientOptions", clientOptions.get("nodes").toString());


      JSONArray _nodes = clientOptions.getJSONArray("nodes");
      String[] nodes = new String[_nodes.length()];
      for (int i = 0; i < _nodes.length(); i++) {
          JSONObject _node = (JSONObject) _nodes.get(i);
          nodes[i] = _node.get("url").toString();
      }

      JSONObject _secretManager = secretManager.getJSONObject("stronghold");
      Log.d("storagePath", path + _secretManager.get("snapshotPath"));
      if (_coinType == null) {
          return;
      }
      CoinType coinType = CoinType.Shimmer;
      if (CoinType.Iota.getCoinTypeValue() == _coinType) {
          coinType = CoinType.Iota;
      }

      try {
          wallet = new Wallet(new WalletConfig()
                  .withClientOptions(new ClientConfig().withNodes(nodes))
                  .withStoragePath(path)
                  .withSecretManager(
                          new StrongholdSecretManager(
                                  null,
                                  null,
                                  path + "/wallet.stronghold"
                          )
                  )
                  .withCoinType(coinType)
          );
          JSObject ret = new JSObject();
          // 1 signals the id of the messageHandler returned by the rust side.
          // This is irrelevant for the Java side, but required on the Swift and JS side
          Integer messageHandlerPointer = 1;
          ret.put("messageHandler", messageHandlerPointer);
          call.resolve(ret);
      } catch (Exception ex) {
          call.reject(ex.getMessage() + Arrays.toString(ex.getStackTrace()));
      }
  }

    @ReactMethod()
    public void sendMessage(final PluginCall call) {
        try {
            if (!call.getData().has("message")) {
                call.reject("message is required");
            }
            String message = call.getString("message");
            if (message == null) {
                return;
            }

            JsonElement element = JsonParser.parseString(message);
            JsonObject jsonObject = element.getAsJsonObject();
            WalletCommand walletCommand;
            if (jsonObject.has("payload") && jsonObject.has("cmd")) {
                        walletCommand = new WalletCommand(
                                jsonObject.get("cmd").getAsString(),
                                jsonObject.get("payload")
                        );
            }
            else {
                walletCommand = new WalletCommand(jsonObject.get("cmd").getAsString());

            }
            JsonElement jsonResponse = callBaseApi(walletCommand);
            JSObject ret = new JSObject();
            if (jsonResponse != null) {
                JsonObject clientResponse = new JsonObject();
                clientResponse.addProperty("type", jsonObject.get("cmd").getAsString());
                clientResponse.add("payload", jsonResponse);
                ret.put("result", clientResponse.toString());
            } else {
                ret.put("result", "ok");
            }
            call.resolve(ret);
        } catch (Exception ex) {
            call.reject(ex.getMessage() + Arrays.toString(ex.getStackTrace()));
            Log.d("sendMessage Error", ex.getMessage() + Arrays.toString(ex.getStackTrace()));
        }
    }

    @ReactMethod(returnType = PluginMethod.RETURN_CALLBACK)
    public void listen(final PluginCall call) throws WalletException, JSONException {
        if (!call.getData().has("eventTypes")) {
            call.reject("eventTypes are required");
        }

        JSArray eventTypes = call.getArray("eventTypes");
        WalletEventType[] types = new WalletEventType[eventTypes.length()];
        for (int i = 0; i < eventTypes.length(); i++) {
            types[i] = WalletEventType.valueOf(eventTypes.getString(i));
        }

        try {
            wallet.listen(new EventListener() {
                @Override
                public void receive(Event event) {
                    JSObject walletResponse = new JSObject();
                    walletResponse.put("result", event.toString());
                    call.resolve(walletResponse);
                }
            }, types);
        } catch (WalletException ex) {
            call.reject(ex.getMessage() + Arrays.toString(ex.getStackTrace()));
        }
        call.setKeepAlive(true);
    }


    @PluginMethod()
    public void destroy(final PluginCall call) {
        try {
            destroyHandle();
            call.release(bridge);
        } catch (Exception ex) {
            call.reject(ex.getMessage() + Arrays.toString(ex.getStackTrace()));
        }
    }
}
