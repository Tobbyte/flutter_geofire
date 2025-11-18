package in.appyflow.geofire;

import android.util.Log;


import androidx.annotation.NonNull;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.firebase.geofire.GeoQueryDataEventListener;
import com.firebase.geofire.LocationCallback;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;


import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * GeofirePlugin
 */
public class GeofirePlugin implements FlutterPlugin,MethodCallHandler, EventChannel.StreamHandler {

    GeoFire geoFire;
    DatabaseReference databaseReference;
    static MethodChannel channel;
    static EventChannel eventChannel;
    private EventChannel.EventSink events;
    
    // Store listener references for selective removal
    private GeoQueryEventListener currentGeoQueryEventListener;
    private GeoQueryDataEventListener currentGeoQueryDataEventListener;

    /**
     * Plugin registration.
     */

    public static void pluginInit(BinaryMessenger messenger){
        GeofirePlugin geofirePlugin = new GeofirePlugin();

        channel = new MethodChannel(messenger, "geofire");
        channel.setMethodCallHandler(geofirePlugin);

        eventChannel = new EventChannel(messenger, "geofireStream");
        eventChannel.setStreamHandler(geofirePlugin);

    }

//    public static void registerWith(Registrar registrar) {
//        pluginInit(registrar.messenger());
//    }

    @Override
    public void onMethodCall(MethodCall call, final Result result) {

        Log.i("TAG", call.method.toString());

        if (call.method.equals("GeoFire.start")) {

            databaseReference = FirebaseDatabase.getInstance().getReference(call.argument("path").toString());
            geoFire = new GeoFire(databaseReference);

            if (geoFire.getDatabaseReference() != null) {
                result.success(true);
            } else
                result.success(false);
        } else if (call.method.equals("setLocation")) {

            geoFire.setLocation(call.argument("id").toString(), new GeoLocation(Double.parseDouble(call.argument("lat").toString()), Double.parseDouble(call.argument("lng").toString())), new GeoFire.CompletionListener() {
                @Override
                public void onComplete(String key, DatabaseError error) {

                    if (error != null) {
                        result.success(false);
                    } else {
                        result.success(true);
                    }

                }
            });


        } else if (call.method.equals("removeLocation")) {

            geoFire.removeLocation(call.argument("id").toString(), new GeoFire.CompletionListener() {
                @Override
                public void onComplete(String key, DatabaseError error) {

                    if (error != null) {
                        result.success(false);
                    } else {
                        result.success(true);
                    }

                }
            });


        } else if (call.method.equals("getLocation")) {

            geoFire.getLocation(call.argument("id").toString(), new LocationCallback() {
                @Override
                public void onLocationResult(String key, GeoLocation location) {
                    HashMap<String, Object> map = new HashMap<>();
                    if (location != null) {


                        map.put("lat", location.latitude);
                        map.put("lng", location.longitude);
                        map.put("error", null);

                    } else {


                        map.put("error", String.format("There is no location for key %s in GeoFire", key));

                    }

                    result.success(map);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("error", "There was an error getting the GeoFire location: " + databaseError);

                    result.success(map);
                }
            });


        } else if (call.method.equals("queryAtLocation")) {
            geoFireArea(Double.parseDouble(call.argument("lat").toString()), Double.parseDouble(call.argument("lng").toString()), result, Double.parseDouble(call.argument("radius").toString()));
        } else if (call.method.equals("queryAtLocationWithData")) {
            geoFireAreaWithData(Double.parseDouble(call.argument("lat").toString()), Double.parseDouble(call.argument("lng").toString()), result, Double.parseDouble(call.argument("radius").toString()));
        } else if (call.method.equals("stopListener")) {

            if (geoQuery != null) {
                geoQuery.removeAllListeners();
                currentGeoQueryEventListener = null;
                currentGeoQueryDataEventListener = null;
            }

            result.success(true);
        } else if (call.method.equals("removeGeoQueryEventListener")) {

            if (geoQuery != null && currentGeoQueryEventListener != null) {
                geoQuery.removeGeoQueryEventListener(currentGeoQueryEventListener);
                currentGeoQueryEventListener = null;
            }

            result.success(true);
        } else if (call.method.equals("removeGeoQueryDataEventListener")) {

            if (geoQuery != null && currentGeoQueryDataEventListener != null) {
                geoQuery.removeGeoQueryEventListener(currentGeoQueryDataEventListener);
                currentGeoQueryDataEventListener = null;
            }

            result.success(true);
        } else {
            result.notImplemented();
        }
    }

    GeoQuery geoQuery;

    HashMap<String, Object> hashMap = new HashMap<>();


    private void geoFireArea(final double latitude, double longitude, final Result result, double radius) {
        try {

            final ArrayList<String> arrayListKeys = new ArrayList<>();

            if (geoQuery != null) {
                // Remove only the current event listener, not data listener
                if (currentGeoQueryEventListener != null) {
                    geoQuery.removeGeoQueryEventListener(currentGeoQueryEventListener);
                    currentGeoQueryEventListener = null;
                }
                geoQuery.setLocation(new GeoLocation(latitude, longitude), radius);
            } else {
                geoQuery = geoFire.queryAtLocation(new GeoLocation(latitude, longitude), radius);
            }

            currentGeoQueryEventListener = new GeoQueryEventListener() {
                @Override
                public void onKeyEntered(String key, GeoLocation location) {

                    if (events != null) {
                        hashMap.clear();
                        hashMap.put("callBack", "onKeyEntered");
                        hashMap.put("key", key);
                        hashMap.put("latitude", location.latitude);
                        hashMap.put("longitude", location.longitude);
                        events.success(hashMap);
                    } else {
                        if (currentGeoQueryEventListener != null) {
                            geoQuery.removeGeoQueryEventListener(currentGeoQueryEventListener);
                            currentGeoQueryEventListener = null;
                        }
                    }

                    arrayListKeys.add(key);

                }

                @Override
                public void onKeyExited(String key) {
                    arrayListKeys.remove(key);

                    if (events != null) {

                        hashMap.clear();
                        hashMap.put("callBack", "onKeyExited");
                        hashMap.put("key", key);
                        events.success(hashMap);
                    } else {
                        if (currentGeoQueryEventListener != null) {
                            geoQuery.removeGeoQueryEventListener(currentGeoQueryEventListener);
                            currentGeoQueryEventListener = null;
                        }
                    }

                }

                @Override
                public void onKeyMoved(String key, GeoLocation location) {

                    if (events != null) {
                        hashMap.clear();

                        hashMap.put("callBack", "onKeyMoved");
                        hashMap.put("key", key);
                        hashMap.put("latitude", location.latitude);
                        hashMap.put("longitude", location.longitude);

                        events.success(hashMap);
                    } else {
                        if (currentGeoQueryEventListener != null) {
                            geoQuery.removeGeoQueryEventListener(currentGeoQueryEventListener);
                            currentGeoQueryEventListener = null;
                        }
                    }

                }

                @Override
                public void onGeoQueryReady() {
//                    geoQuery.removeAllListeners();
//                    result.success(arrayListKeys);

                    if (events != null) {
                        hashMap.clear();

                        hashMap.put("callBack", "onGeoQueryReady");
                        hashMap.put("result", arrayListKeys);

                        events.success(hashMap);

                    } else {
                        if (currentGeoQueryEventListener != null) {
                            geoQuery.removeGeoQueryEventListener(currentGeoQueryEventListener);
                            currentGeoQueryEventListener = null;
                        }
                    }

                }

                @Override
                public void onGeoQueryError(DatabaseError error) {

                    if (events != null) {

                        events.error("Error ", "GeoQueryError", error);
                    } else {
                        if (currentGeoQueryEventListener != null) {
                            geoQuery.removeGeoQueryEventListener(currentGeoQueryEventListener);
                            currentGeoQueryEventListener = null;
                        }
                    }


                }
            };
            
            geoQuery.addGeoQueryEventListener(currentGeoQueryEventListener);
        } catch (Exception e) {
            e.printStackTrace();
            result.error("Error ", "General Error", e);
        }
    }

    private void geoFireAreaWithData(final double latitude, double longitude, final Result result, double radius) {
        try {

            final ArrayList<String> arrayListKeys = new ArrayList<>();

            if (geoQuery != null) {
                // Remove only the current data listener, not event listener
                if (currentGeoQueryDataEventListener != null) {
                    geoQuery.removeGeoQueryEventListener(currentGeoQueryDataEventListener);
                    currentGeoQueryDataEventListener = null;
                }
                geoQuery.setLocation(new GeoLocation(latitude, longitude), radius);
            } else {
                geoQuery = geoFire.queryAtLocation(new GeoLocation(latitude, longitude), radius);
            }

            currentGeoQueryDataEventListener = new GeoQueryDataEventListener() {
                @Override
                public void onDataEntered(DataSnapshot dataSnapshot, GeoLocation location) {

                    if (events != null) {
                        hashMap.clear();
                        hashMap.put("callBack", "onDataKeyEntered");
                        hashMap.put("key", dataSnapshot.getKey());
                        hashMap.put("latitude", location.latitude);
                        hashMap.put("longitude", location.longitude);
                        
                        // Convert DataSnapshot to HashMap
                        HashMap<String, Object> dataMap = new HashMap<>();
                        if (dataSnapshot.getValue() != null) {
                            try {
                                Object value = dataSnapshot.getValue();
                                if (value instanceof HashMap) {
                                    dataMap = (HashMap<String, Object>) value;
                                } else {
                                    dataMap.put("value", value);
                                }
                            } catch (Exception e) {
                                dataMap.put("value", dataSnapshot.getValue());
                            }
                        }
                        hashMap.put("data", dataMap);
                        
                        events.success(hashMap);
                    } else {
                        if (currentGeoQueryDataEventListener != null) {
                            geoQuery.removeGeoQueryEventListener(currentGeoQueryDataEventListener);
                            currentGeoQueryDataEventListener = null;
                        }
                    }

                    arrayListKeys.add(dataSnapshot.getKey());

                }

                @Override
                public void onDataExited(DataSnapshot dataSnapshot) {
                    arrayListKeys.remove(dataSnapshot.getKey());

                    if (events != null) {

                        hashMap.clear();
                        hashMap.put("callBack", "onDataKeyExited");
                        hashMap.put("key", dataSnapshot.getKey());
                        
                        // Convert DataSnapshot to HashMap
                        HashMap<String, Object> dataMap = new HashMap<>();
                        if (dataSnapshot.getValue() != null) {
                            try {
                                Object value = dataSnapshot.getValue();
                                if (value instanceof HashMap) {
                                    dataMap = (HashMap<String, Object>) value;
                                } else {
                                    dataMap.put("value", value);
                                }
                            } catch (Exception e) {
                                dataMap.put("value", dataSnapshot.getValue());
                            }
                        }
                        hashMap.put("data", dataMap);
                        
                        events.success(hashMap);
                    } else {
                        if (currentGeoQueryDataEventListener != null) {
                            geoQuery.removeGeoQueryEventListener(currentGeoQueryDataEventListener);
                            currentGeoQueryDataEventListener = null;
                        }
                    }

                }

                @Override
                public void onDataMoved(DataSnapshot dataSnapshot, GeoLocation location) {

                    if (events != null) {
                        hashMap.clear();

                        hashMap.put("callBack", "onDataKeyMoved");
                        hashMap.put("key", dataSnapshot.getKey());
                        hashMap.put("latitude", location.latitude);
                        hashMap.put("longitude", location.longitude);

                        // Convert DataSnapshot to HashMap
                        HashMap<String, Object> dataMap = new HashMap<>();
                        if (dataSnapshot.getValue() != null) {
                            try {
                                Object value = dataSnapshot.getValue();
                                if (value instanceof HashMap) {
                                    dataMap = (HashMap<String, Object>) value;
                                } else {
                                    dataMap.put("value", value);
                                }
                            } catch (Exception e) {
                                dataMap.put("value", dataSnapshot.getValue());
                            }
                        }
                        hashMap.put("data", dataMap);

                        events.success(hashMap);
                    } else {
                        if (currentGeoQueryDataEventListener != null) {
                            geoQuery.removeGeoQueryEventListener(currentGeoQueryDataEventListener);
                            currentGeoQueryDataEventListener = null;
                        }
                    }

                }

                @Override
                public void onDataChanged(DataSnapshot dataSnapshot, GeoLocation location) {
                    // Called when the data at a location has changed
                    if (events != null) {
                        hashMap.clear();

                        hashMap.put("callBack", "onDataKeyChanged");
                        hashMap.put("key", dataSnapshot.getKey());
                        hashMap.put("latitude", location.latitude);
                        hashMap.put("longitude", location.longitude);

                        // Convert DataSnapshot to HashMap
                        HashMap<String, Object> dataMap = new HashMap<>();
                        if (dataSnapshot.getValue() != null) {
                            try {
                                Object value = dataSnapshot.getValue();
                                if (value instanceof HashMap) {
                                    dataMap = (HashMap<String, Object>) value;
                                } else {
                                    dataMap.put("value", value);
                                }
                            } catch (Exception e) {
                                dataMap.put("value", dataSnapshot.getValue());
                            }
                        }
                        hashMap.put("data", dataMap);

                        events.success(hashMap);
                    } else {
                        if (currentGeoQueryDataEventListener != null) {
                            geoQuery.removeGeoQueryEventListener(currentGeoQueryDataEventListener);
                            currentGeoQueryDataEventListener = null;
                        }
                    }
                }

                @Override
                public void onGeoQueryReady() {

                    if (events != null) {
                        hashMap.clear();

                        hashMap.put("callBack", "onGeoQueryReady");
                        hashMap.put("result", arrayListKeys);

                        events.success(hashMap);

                    } else {
                        if (currentGeoQueryDataEventListener != null) {
                            geoQuery.removeGeoQueryEventListener(currentGeoQueryDataEventListener);
                            currentGeoQueryDataEventListener = null;
                        }
                    }

                }

                @Override
                public void onGeoQueryError(DatabaseError error) {

                    if (events != null) {

                        events.error("Error ", "GeoQueryError", error);
                    } else {
                        if (currentGeoQueryDataEventListener != null) {
                            geoQuery.removeGeoQueryEventListener(currentGeoQueryDataEventListener);
                            currentGeoQueryDataEventListener = null;
                        }
                    }


                }
            };
            
            geoQuery.addGeoQueryDataEventListener(currentGeoQueryDataEventListener);
        } catch (Exception e) {
            e.printStackTrace();
            result.error("Error ", "General Error", e);
        }
    }

    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        events = eventSink;
    }

    @Override
    public void onCancel(Object o) {

        geoQuery.removeAllListeners();
        events = null;

    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        pluginInit(binding.getBinaryMessenger());
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        eventChannel.setStreamHandler(null);
    }
}