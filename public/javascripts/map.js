angular.module("clustermap.map", ["leaflet-directive", "clustermap.common"])
  .controller("MapCtrl", function($scope, $timeout, leafletData, moduleManager) {

    $scope.radius = 20; // cluster radius in pixels
    $scope.extent = 256; // tile extent (radius is calculated relative to it)
    $scope.maxZoom = 18;
    $scope.zoomShift = 0;
    $scope.mode = "middleware"; // "frontend" / "middleware"
    $scope.mwVisualizationType = "cluster"; // "cluster" / "heat" / "scatter"
    $scope.feVisualizationType = "cluster"; // "cluster" / "heat" / "scatter"
    $scope.scatterType = "gl-pixel"; // "gl-pixel" / "gl-raster" / "leaflet" / "deck-gl"
    $scope.numberInCircle = true;
    $scope.colorEncoding = true;
    $scope.circleRadius = 20;
    $scope.scaleCircleRadius = false;
    $scope.recording = false; // whether it's recording zoom/pan actions
    $scope.actions = []; // zoom/pan actions recorded
    $scope.replaying = false; // whether it's replaying recorded zoom/pan actions
    $scope.replayingIndex = 0; // keep current index of replaying

    // store total pointsCount for "frontend" mode
    $scope.pointsCount = 0;
    // timing for "frontend" mode
    $scope.timings = [];
    // timing for query
    $scope.queryStart = 0.0;
    // timing for rendering
    $scope.renderStart = 0.0;
    // timing for actions
    $scope.timeActions = false;
    $scope.actionTime = {};
    $scope.actionTimings = [];

    // wsFormat
    $scope.wsFormat = "binary"; // "array" (json) / "binary"

    // store request object for handle websocket onMessage
    $scope.request = {};

    // store query object for "middleware" mode
    $scope.query = {
      clusterKey: "",
      order: "",
      algorithm: "",
      zoom: 0,
      bbox: [],
      indexType: "",
      treeCut: false
    };

    $scope.ws = new WebSocket("ws://" + location.host + "/ws");
    $scope.ws.binaryType = 'arraybuffer';

    // store computed radius of each zoom level
    $scope.radiuses = [];
    for (let i = 0; i < $scope.maxZoom; i ++) {
      $scope.radiuses[i] = $scope.radius / ($scope.extent * Math.pow(2, i));
    }

    /** middleware mode */
    $scope.sendQuery = function(e) {
      console.log("e = " + JSON.stringify(e));

      if (e.keyword) {
        $scope.query.keyword = e.keyword;
      }

      if (e.order) {
        $scope.query.order = e.order;
      }

      if ($scope.map) {
        $scope.query.zoom = $scope.map.getZoom() + $scope.zoomShift;
        $scope.query.bbox = [
          $scope.map.getBounds().getWest(),
          $scope.map.getBounds().getSouth(),
          $scope.map.getBounds().getEast(),
          $scope.map.getBounds().getNorth()
        ];

        $scope.query.resX = $scope.map.getSize().x;
        $scope.query.resY = $scope.map.getSize().y;
      }

      if (e.algorithm) {
        $scope.query.algorithm = e.algorithm;
      }

      if (e.indexType) {
        $scope.query.indexType = e.indexType;
      }

      if (e.hasOwnProperty("treeCut")) {
        $scope.query.treeCut = e.treeCut;
      }

      if (e.measure) {
        $scope.query.measure = e.measure;
      }

      if (e.pixels) {
        $scope.query.pixels = e.pixels;
      }

      if (e.hasOwnProperty("bipartite")) {
        $scope.query.bipartite = e.bipartite;
      }

      $scope.query.aggregator = $scope.scatterType;

      // only send query when comprised query has enough information, i.e. keyword, order, algorithm
      if ($scope.query.keyword && $scope.query.order && $scope.query.algorithm) {
        $scope.query.clusterKey = $scope.query.keyword + "-" + $scope.query.order + "-" + $scope.query.algorithm;

        let request = {
          type: "query",
          id: $scope.query.keyword,
          keyword: $scope.query.keyword,
          query: $scope.query,
          format: $scope.mwVisualizationType === "cluster" ? "geojson" : $scope.wsFormat
        };

        // if e.analysis is not "", comparing the given algorithm with SuperCluster using e.analysis indicated function
        if (e.analysis !== "" && request.query.algorithm.toLowerCase() !== "supercluster") {
          request.analysis = {
            objective: e.analysis,
            arguments: [
              request.query.keyword + "-" + request.query.order + "-SuperCluster",
              request.query.clusterKey,
              e.zoom
            ]
          };
        }

        console.log("sending query:");
        console.log(JSON.stringify(request));

        // timing query
        $scope.queryStart = performance.now();

        $scope.ws.send(JSON.stringify(request));

        $scope.request = request;

        document.getElementById("myBar").style.width = "0%";
      }
    };

    /** frontend mode */
    $scope.sendProgressTransfer = function(e) {
      console.log("e = " + JSON.stringify(e));

      let request = {
        type: "progress-transfer",
        id: "progress-transfer-" + e.keyword,
        keyword: e.keyword,
        format: "array"
      };

      if (e.keyword) {
        $scope.query.keyword = e.keyword;
      }

      console.log("sending progress-transfer:");
      console.log(JSON.stringify(request));

      $scope.ws.send(JSON.stringify(request));

      $scope.request = request;

      document.getElementById("myBar").style.width = "0%";
      $scope.pointsCount = 0;
      $scope.timings = [];
    };

    $scope.sendCmd = function(id, keyword, commands) {
      let request = {
        type: "cmd",
        id: id,
        keyword: keyword,
        cmds: commands
      };

      console.log("sending cmd:");
      console.log(JSON.stringify(request));

      $scope.ws.send(JSON.stringify(request));

      $scope.request = request;
    };

    $scope.waitForWS = function() {

      if ($scope.ws.readyState !== $scope.ws.OPEN) {
        window.setTimeout($scope.waitForWS, 1000);
      }
      else {
        moduleManager.publishEvent(moduleManager.EVENT.WS_READY, {});

        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_SEARCH_KEYWORD, function(e) {
          switch ($scope.mode) {
            case "frontend":
              $scope.cleanMWLayers();
              $scope.sendProgressTransfer(e);
              break;
            case "middleware":
              $scope.cleanFELayers();
              $scope.sendQuery(e);
              break;
          }
        });

        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_ZOOM_LEVEL, function(e) {
          switch ($scope.mode) {
            case "frontend":
              break;
            case "middleware":
              $scope.sendQuery(e);
              break;
          }
        });

        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_ZOOM_SHIFT, function(e) {
          switch ($scope.mode) {
            case "frontend":
              break;
            case "middleware":
              $scope.zoomShift = parseInt(e.zoomShift);
              $scope.sendQuery(e);
              break;
          }
        });

        moduleManager.subscribeEvent(moduleManager.EVENT.CONSOLE_INPUT, function(e) {
          console.log("sending console command:");
          console.log(JSON.stringify(e));
          $scope.ws.send(JSON.stringify(e));
        });

        $scope.map.on('moveend', function() {
          // record zoom/pan actions
          if ($scope.recording) {
            $scope.actions.push({center: $scope.map.getCenter(), zoom: $scope.map.getZoom()});
          }
          // if ($scope.replaying) {
          //   moduleManager.publishEvent(moduleManager.EVENT.FINISH_ACTION, {});
          // }
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_ZOOM_LEVEL, {zoom: $scope.map.getZoom()});

          console.log("viewport changed to: center = " + JSON.stringify($scope.map.getCenter()) + ", zoom = " + $scope.map.getZoom());
        });

        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_MODE, function(e) {
          if ($scope.mode !== e.mode) {
            if ($scope.mode === "middleware") {
              $scope.cleanMWLayers();
            }
            else if ($scope.mode === "frontend") {
              $scope.cleanFELayers();
            }
            $scope.mode = e.mode;
            console.log("switch mode to '" + $scope.mode + "'");
          }
        });

        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_NUMBER_IN_CIRCLE, function(e) {
          console.log("switch number in circle to '" + e.numberInCircle + "'");
          $scope.numberInCircle = e.numberInCircle;
          $scope.cleanClusterLayer();
          if ($scope.clusters) {
            $scope.drawMWClusterLayer($scope.clusters);
          }
        });

        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_COLOR_ENCODING, function(e) {
          console.log("switch color encoding to '" + e.colorEncoding + "'");
          $scope.colorEncoding = e.colorEncoding;
          // only affects no-number in circle mode
          if (!$scope.numberInCircle) {
            $scope.cleanClusterLayer();
            if ($scope.clusters) {
              $scope.drawMWClusterLayer($scope.clusters);
            }
          }
        });

        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_CIRCLE_RADIUS, function(e) {
          console.log("switch circle radius to " + e.circleRadius);
          $scope.circleRadius = e.circleRadius;
          switch ($scope.mode) {
            case "frontend":
              switch ($scope.feVisualizationType) {
                case "cluster":
                  break;
                case "heat":
                  break;
                case "scatter":
                  $scope.cleanScatterLayer();
                  if ($scope.rawData) {
                    $scope.drawFEScatterLayer($scope.rawData);
                  }
                  break;
              }
              break;
            case "middleware":
              switch ($scope.mwVisualizationType) {
                case "cluster":
                  // only affects no-number in circle mode
                  if (!$scope.numberInCircle) {
                    $scope.cleanClusterLayer();
                    if ($scope.clusters) {
                      $scope.drawMWClusterLayer($scope.clusters);
                    }
                  }
                  break;
                case "heat":
                  $scope.cleanHeatLayer();
                  if ($scope.points) {
                    $scope.drawMWHeatLayer($scope.points);
                  }
                  break;
                case "scatter":
                  $scope.cleanScatterLayer();
                  if ($scope.points) {
                    $scope.drawMWScatterLayer($scope.points);
                  }
                  break;
              }
              break;
          }
        });

        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_SCALE_CIRCLE_RADIUS, function(e) {
          console.log("switch scale circle radius to " + e.scaleCircleRadius);
          $scope.scaleCircleRadius = e.scaleCircleRadius;
          // only affects no-number in circle mode
          if (!$scope.numberInCircle) {
            $scope.cleanClusterLayer();
            if ($scope.clusters) {
              $scope.drawMWClusterLayer($scope.clusters);
            }
          }
        });

        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_MW_VISUALIZATION_TYPE, function(e) {
          console.log("switch middleware visualization type to " + e.mwVisualizationType);
          $scope.mwVisualizationType = e.mwVisualizationType;
          if ($scope.mode === "middleware") {
            switch (e.mwVisualizationType) {
              case "cluster":
                $scope.cleanHeatLayer();
                $scope.cleanScatterLayer();
                if ($scope.clusters) {
                  $scope.drawMWClusterLayer($scope.clusters);
                }
                break;
              case "heat":
                $scope.cleanClusterLayer();
                $scope.cleanScatterLayer();
                if ($scope.points) {
                  $scope.drawMWHeatLayer($scope.points);
                }
                break;
              case "scatter":
                $scope.cleanClusterLayer();
                $scope.cleanHeatLayer();
                if ($scope.points) {
                  $scope.drawMWScatterLayer($scope.points);
                }
                break;
            }
          }
        });

        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_FE_VISUALIZATION_TYPE, function(e) {
          console.log("switch frontend visualization type to " + e.feVisualizationType);
          $scope.feVisualizationType = e.feVisualizationType;
          if ($scope.mode === "frontend") {
            switch (e.feVisualizationType) {
              case "cluster":
                $scope.cleanHeatLayer();
                $scope.cleanScatterLayer();
                if ($scope.rawData) {
                  $scope.drawFEClusterLayer($scope.rawData);
                }
                break;
              case "heat":
                $scope.cleanClusterLayer();
                $scope.cleanScatterLayer();
                if ($scope.rawData) {
                  $scope.drawFEHeatLayer($scope.rawData);
                }
                break;
              case "scatter":
                $scope.cleanClusterLayer();
                $scope.cleanHeatLayer();
                if ($scope.rawData) {
                  $scope.drawFEScatterLayer($scope.rawData);
                }
                break;
            }
          }
        });

        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_SCATTER_TYPE, function(e) {
          console.log("switch scatter type to " + e.scatterType);
          switch ($scope.mode) {
            case "frontend":
              $scope.cleanScatterLayer();
              $scope.scatterType = e.scatterType;
              if ($scope.rawData) {
                $scope.drawFEScatterLayer($scope.rawData);
              }
              break;
            case "middleware":
              $scope.cleanScatterLayer();
              $scope.scatterType = e.scatterType;
              if ($scope.points) {
                $scope.drawMWScatterLayer($scope.points);
              }
              break;
          }
        });
      }
    };

    // setting default map styles, zoom level, etc.
    angular.extend($scope, {
      tiles: {
        name: 'Mapbox',
        url: 'https://api.tiles.mapbox.com/v4/{id}/{z}/{x}/{y}.png?access_token={accessToken}',
        type: 'xyz',
        options: {
          accessToken: 'pk.eyJ1IjoiamVyZW15bGkiLCJhIjoiY2lrZ2U4MWI4MDA4bHVjajc1am1weTM2aSJ9.JHiBmawEKGsn3jiRK_d0Gw',
          id: 'jeremyli.p6f712pj'
        }
      },
      controls: {
        custom: []
      }
    });

    /** colored map style */
    // angular.extend($scope, {
    //   tiles: {
    //     name: 'OpenStreetMap',
    //     url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    //     attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
    //   },
    //   controls: {
    //     custom: []
    //   }
    // });

    // initialize the leaflet map
    $scope.init = function() {
      leafletData.getMap().then(function (map) {
        $scope.map = map;
        $scope.bounds = map.getBounds();
        //making attribution control to false to remove the default leaflet sign in the bottom of map
        map.attributionControl.setPrefix(false);
        map.setView([$scope.lat, $scope.lng], $scope.zoom);
      });

      //Reset Zoom Button
      var button = document.createElement("a");
      var text = document.createTextNode("Reset");
      button.appendChild(text);
      button.title = "Reset";
      button.style.position = 'fixed';
      button.style.top = '70px';
      button.style.left = '8px';
      button.style.fontSize = '14px';
      var body = document.body;
      body.appendChild(button);
      button.addEventListener("click", function () {
        $scope.map.setView([$scope.lat, $scope.lng], 4, {animate: true});
      });

      // handler for record button
      moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_RECORDING, function(e) {
        console.log("recording status changed: " + e.recording);
        $scope.recording = e.recording;
        if (!$scope.recording) {
          console.log("===== recorded actions =====");
          for (let i = 0; i < $scope.actions.length; i ++) {
            console.log("[" + i + "] " + JSON.stringify($scope.actions[i]));
          }
          console.log("===== recorded actions json =====");
          console.log(JSON.stringify($scope.actions));

          function saveText(text, filename){
            let a = document.createElement('a');
            a.setAttribute('href', 'data:text/plain;charset=utf-u,'+encodeURIComponent(text));
            a.setAttribute('download', filename);
            a.click();
          }

          saveText(JSON.stringify($scope.actions), "actions.json");
        }
      });

      // handler for replay button
      moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_REPLAYING, function(e) {

        function replayNextAction() {
          if ($scope.replayingIndex >= $scope.actions.length) {
            console.log("Stop replaying!");
            moduleManager.unsubscribeEvent(moduleManager.EVENT.FINISH_ACTION, finishAction);
            $scope.replayingIndex = 0;
            moduleManager.publishEvent(moduleManager.EVENT.FINISH_REPLAY, {});
            return;
          }
          let action = $scope.actions[$scope.replayingIndex];
          console.log("[" + $scope.replayingIndex + "] replaying action : " + JSON.stringify(action));
          $scope.replayingIndex ++;
          $scope.map.setView(action.center, action.zoom, {animate: true});
        }

        function finishAction(e) {
          console.log("Action [" + ($scope.replayingIndex - 1) + "] is done!");
          setTimeout(replayNextAction, 1000);
        }

        console.log("replaying status changed: " + e.replaying);
        $scope.replaying = e.replaying;
        if ($scope.replaying) {
          // start replaying zoom/pan actions
          console.log("Now start replaying actions ...");
          $scope.timeActions = true;
          $scope.actionTimings = [];
          moduleManager.subscribeEvent(moduleManager.EVENT.FINISH_ACTION, finishAction);
          replayNextAction();
        }
        else {
          // stop replaying
          console.log("Stop replaying!");
          moduleManager.unsubscribeEvent(moduleManager.EVENT.FINISH_ACTION, finishAction);
          $scope.replayingIndex = 0;
        }
      });

      // handler for load button
      moduleManager.subscribeEvent(moduleManager.EVENT.LOAD_ACTIONS, function(e) {
        $scope.actions = e.actions;
        console.log("===== Actions loaded =====");
        console.log(JSON.stringify($scope.actions));
      });

      // handler for finish replay
      moduleManager.subscribeEvent(moduleManager.EVENT.FINISH_REPLAY, function(e) {
        console.log("===== Actions timings (json) =====");
        console.log(JSON.stringify($scope.actionTimings));
        console.log("===== Actions timings (csv) =====");
        let output = "zoom,    serverTime,    treeCutTime,    aggregateTime,    networkTime,    renderTime\n";
        for (let i = 0; i < $scope.actionTimings.length; i ++) {
          output += $scope.actions[i].zoom + ",    " +
            $scope.actionTimings[i].serverTime + ",    " +
            $scope.actionTimings[i].treeCutTime + ",    " +
            $scope.actionTimings[i].aggregateTime + ",    " +
            $scope.actionTimings[i].networkTime + ",    " +
            $scope.actionTimings[i].renderTime + "\n";
        }
        console.log(output);
        console.log("===========================");
      });

      $scope.waitForWS();
    };

    /** middleware mode */
    $scope.handleResult = function(result) {
      if(result.data.length > 0) {
        let resultCount = result.data.length;
        let pointsCount = 0;
        let maxCount = 0;
        for (let i = 0; i < resultCount; i ++) {
          let count;
          if ($scope.mwVisualizationType === "cluster") {
            count = result.data[i].properties.point_count === 0? 1: result.data[i].properties.point_count;
          }
          else {
            count = result.data[i][2] === 0? 1: result.data[i][2];
          }
          pointsCount += count;
          maxCount = Math.max(maxCount, count);
        }
        $scope.pointsCount = pointsCount;
        $scope.maxCount = maxCount;
        $scope.avgPointsCount = pointsCount / resultCount;
        moduleManager.publishEvent(moduleManager.EVENT.CHANGE_RESULT_COUNT, {resultCount: resultCount, pointsCount: pointsCount});
        switch ($scope.mwVisualizationType) {
          case "cluster":
            $scope.drawMWClusterLayer(result.data);
            break;
          case "heat":
            $scope.drawMWHeatLayer(result.data);
            break;
          case "scatter":
            $scope.drawMWScatterLayer(result.data);
            break;
        }
      }
    };

    /** frontend mode */
    $scope.handleProgressTransfer = function(result) {
      if(result.data.length > 0) {
        $scope.pointsCount += result.data.length;
        moduleManager.publishEvent(moduleManager.EVENT.CHANGE_RESULT_COUNT, {pointsCount: $scope.pointsCount});
        switch ($scope.feVisualizationType) {
          case "cluster":
            $scope.drawFEClusterLayer(result.data);
            break;
          case "heat":
            $scope.drawFEHeatLayer(result.data);
            break;
          case "scatter":
            $scope.drawFEScatterLayer(result.data);
            break;
        }
      }
    };

    $scope.parseBinary = function(binaryData) {
      // ---------------------------- data ----------------------------
      //  progress  totalTime  treeCut   aggTime   binary data payload
      // | 4 BYTES | 8 BYTES | 8 BYTES | 8 BYTES | ...
      // ---- binary data payload ----
      //    lat        lng    numPoints
      // | 8 BYTES | 8 BYTES | 4 BYTES | ...(repeat)...
      let dv = new DataView(binaryData);
      let response = {};
      let j = 0; // offset by bytes
      response.progress = dv.getInt32(j);
      j = j + 4;
      response.totalTime = dv.getFloat64(j);
      j = j + 8;
      response.treeCutTime = dv.getFloat64(j);
      j = j + 8;
      response.aggregateTime = dv.getFloat64(j);
      j = j + 8;
      const headerSize = 4 + 8 + 8 + 8;
      const recordSize = 8 + 8 + 4;
      let dataLength = (dv.byteLength - headerSize) / recordSize;
      let data = [];
      for (let i = 0; i < dataLength; i ++) {
        // current record's starting offset
        j = headerSize + recordSize * i;
        let record = [];
        record.push(dv.getFloat64(j)); // lat
        j = j + 8;
        record.push(dv.getFloat64(j)); // lng
        j = j + 8;
        record.push(dv.getInt32(j)); // numPoints
        data.push(record);
      }
      response.result = {data: data};
      console.log("==== websocket received binary data ====");
      console.log(binaryData);
      console.log("size = " + (headerSize + dataLength * recordSize) / (1024.0 * 1024.0) + " MB.");
      return response;
    };

    $scope.ws.onmessage = function(event) {
      $timeout(function() {

        // timing for actions
        let queryEnd = performance.now();
        let queryTime = (queryEnd - $scope.queryStart) / 1000.0; // seconds

        let response = {};
        switch ($scope.request.type) {
          case "query":
            switch ($scope.request.format) {
              case "array":
                response = JSON.parse(event.data);
                break;
              case "binary":
                response = $scope.parseBinary(event.data);
                break;
            }
            break;
          default:
            response = JSON.parse(event.data);
            break;
        }

        /**
         * response: {
         *   result: {
         *     data: [
         *       [lat1, lng1, count1],
         *       [lat2, lng2, count2],
         *       ...
         *     ]
         *   }
         * }
         */
        console.log("===== websocket response =====");
        console.log(JSON.stringify(response));

        const serverTime = response.totalTime;
        const treeCutTime = response.treeCutTime;
        const aggregateTime = response.aggregateTime;
        const networkTime = queryTime - serverTime;
        console.log("===== query time =====");
        console.log("serverTime: " + serverTime + " seconds.");
        console.log("treeCutTime: " + treeCutTime + " seconds.");
        console.log("aggregateTime: " + aggregateTime + " seconds.");
        console.log("networkTime: " + networkTime + " seconds.");
        if ($scope.timeActions) {
          $scope.actionTime = {
            serverTime: serverTime,
            treeCutTime: treeCutTime,
            aggregateTime: aggregateTime,
            networkTime: networkTime
          };
        }

        switch ($scope.request.type) {
          case "query":
            $scope.handleResult(response.result);
            if (typeof response.progress == "number") {
              document.getElementById("myBar").style.width = response.progress + "%";
            }
            break;
          case "cmd":
            if (response.id === "console") {
              moduleManager.publishEvent(moduleManager.EVENT.CONSOLE_OUTPUT, response);
            }
            break;
          case "progress-transfer":
            $scope.handleProgressTransfer(response.result);
            if (typeof response.progress == "number") {
              document.getElementById("myBar").style.width = response.progress + "%";
            }
            break;
        }
      });
    };

    /** middleware mode */
    // function for drawing cluster plot layer
    $scope.drawMWClusterLayer = function(data) {
      console.log("Radius for zoom level [" + $scope.map.getZoom() + "] = " + $scope.radiuses[$scope.map.getZoom()]);

      // timing for rendering
      $scope.renderStart = performance.now();

      // initialize the clusters layer
      if (!$scope.clusterLayer) {
        if ($scope.numberInCircle) {
          $scope.clusterLayer = L.geoJson(null, { pointToLayer: $scope.createFixedSizedClusterIcon}).addTo($scope.map);
        }
        else {
          $scope.clusterLayer = L.geoJson(null, { pointToLayer: $scope.createVariedSizedClusterIcon}).addTo($scope.map);
        }
        $scope.clusters = [];
      }

      // update the clusters layer
      if (data.length > 0) {
        $scope.clusters = data;
        console.log("drawing clusters size = " + data.length);
        //console.log($scope.clusters);
        $scope.clusterLayer.clearLayers();
        $scope.clusterLayer.addData(data);
      }

      // analysis of distance between clicked clusters
      $scope.clusterLayer.on('contextmenu', (e) => {
        if (e.layer.feature.properties.id) {
          if ($scope.selectedCount !== 1) {
            $scope.p1 = e.layer.feature.properties.id;
            console.log("point1 id[" + $scope.p1 + "] selected!");
            $scope.selectedCount = 1;
          }
          else if ($scope.selectedCount === 1 && e.layer.feature.properties.id !== $scope.p1) {
            $scope.p2 = e.layer.feature.properties.id;
            console.log("point2 id[" + $scope.p2 + "] selected!");
            $scope.selectedCount = 2;
          }
          if ($scope.selectedCount === 2) {
            let analysis = {
              type: "analysis",
              keyword: $scope.keyword,
              analysis: {
                objective: "distance",
                arguments: [
                  $scope.query.clusterKey, // clusterKey
                  $scope.map.getZoom() + 1, // zoom
                  $scope.p1, // point1_id
                  $scope.p2
                ]
              }
            };
            console.log("sending analysis:");
            console.log(JSON.stringify(analysis));
            $scope.ws.send(JSON.stringify(analysis));
          }
        }
      });

      $scope.clusterLayer.on('click', (e) => {
        // zoom in if click on a cluster
        if (e.layer.feature.properties.point_count) {
          $scope.map.setView(e.latlng, e.layer.feature.properties.expansionZoom, {animate: true});
        }
      });

      // timing for rendering
      let renderEnd = performance.now();
      let renderTime = (renderEnd - $scope.renderStart) / 1000.0; // seconds
      console.log("renderTime: " + renderTime + " seconds.");
      if ($scope.timeActions) {
        $scope.actionTime.renderTime = renderTime;
        $scope.actionTimings.push($scope.actionTime);
      }
      if ($scope.replaying) {
        moduleManager.publishEvent(moduleManager.EVENT.FINISH_ACTION, {});
      }

      return 0;
    };

    /** middleware mode */
    // function for drawing heatmap layer
    $scope.drawMWHeatLayer = function(data) {
      // timing for rendering
      $scope.renderStart = performance.now();

      // initialize the heat layer
      if (!$scope.heatLayer) {
        let circleRadius = $scope.circleRadius * 0.7;
        $scope.heatLayer = L.heatLayer([], {radius: circleRadius}).addTo($scope.map);
        $scope.points = [];
      }

      // update the heat layer
      if (data.length > 0) {
        $scope.points = data; // [lng, lat, point_count]
        console.log("[draw heatmap] drawing points size = " + data.length);
        // construct consumable points array for heat layer
        let points = [];
        for (let i = 0; i < data.length; i ++) {
          let point = data[i];
          points.push([point[0], point[1], point[2] == 0? 1: point[2]]);
        }
        // redraw heat layer
        $scope.heatLayer.setLatLngs(points);
        $scope.heatLayer.redraw();
      }

      // timing for rendering
      let renderEnd = performance.now();
      let renderTime = (renderEnd - $scope.renderStart) / 1000.0; // seconds
      console.log("renderTime: " + renderTime + " seconds.");
      if ($scope.timeActions) {
        $scope.actionTime.renderTime = renderTime;
        $scope.actionTimings.push($scope.actionTime);
      }
      if ($scope.replaying) {
        moduleManager.publishEvent(moduleManager.EVENT.FINISH_ACTION, {});
      }
    };

    /** middleware mode */
    // function for drawing scatter plot layer
    $scope.drawMWScatterLayer = function(data) {
      // timing for rendering
      $scope.renderStart = performance.now();

      // initialize the scatter layer
      if (!$scope.scatterLayer) {
        let circleRadius = $scope.circleRadius;
        switch ($scope.scatterType) {
          case "gl-pixel":
            $scope.scatterLayer = new WebGLPointLayer({renderMode: "pixel"}); // renderMode: raster / pixel
            $scope.scatterLayer.setPointSize(2 * circleRadius);
            $scope.scatterLayer.setPointColor(0, 0, 255);
            $scope.map.addLayer($scope.scatterLayer);
            break;
          case "gl-raster":
            $scope.scatterLayer = new WebGLPointLayer({renderMode: "raster"}); // renderMode: raster / pixel
            $scope.scatterLayer.setPointSize(2 * circleRadius);
            $scope.scatterLayer.setPointColor(0, 0, 255);
            $scope.map.addLayer($scope.scatterLayer);
            break;
          case "leaflet":
            $scope.scatterLayer = L.TileLayer.maskCanvas({
              radius: circleRadius,  // radius in pixels or in meters (see useAbsoluteRadius)
              useAbsoluteRadius: false,  // true: r in meters, false: r in pixels
              color: 'blue',  // the color of the layer
              opacity: 1.0,  // opacity of the not covered area
              noMask: true//,  // true results in normal (filled) circled, instead masked circles
              //lineColor: 'blue'   // color of the circle outline if noMask is true
            });
            $scope.map.addLayer($scope.scatterLayer);
            break;
          case "deck-gl":
            $scope.scatterLayer = new deck.DeckGL({
              container: 'deck-gl-canvas',
              initialViewState: {
                latitude: $scope.map.getCenter().lat,
                longitude: $scope.map.getCenter().lng,
                zoom: $scope.map.getZoom() - 1,
                bearing: 0,
                pitch: 0
              },
              controller: true,
              layers: []
            });
            break;
        }
        $scope.points = [];
      }

      // update the scatter layer
      if (data.length > 0) {
        $scope.points = data; // [lat, lng, point_count]
        console.log("[draw scatterplot] drawing points size = " + data.length);
        // construct consumable points array for scatter layer
        let points = []; // [lat, lng, id]
        for (let i = 0; i < data.length; i ++) {
          let point = data[i];
          points.push([point[0], point[1], i]);
        }
        // redraw scatter layer
        switch ($scope.scatterType) {
          case "gl-pixel":
            $scope.scatterLayer.appendData(points);
            break;
          case "gl-raster":
            $scope.scatterLayer.appendData(points);
            break;
          case "leaflet":
            $scope.scatterLayer.setData(points);
            break;
          case "deck-gl":
            const deckGLScatterplot = new deck.ScatterplotLayer({
              /* unique id of this layer */
              id: 'deck-gl-scatter',
              data: points,
              /* data accessors */
              radiusMinPixels: Math.round($scope.circleRadius),
              getPosition: d => [d[1], d[0]],     // returns longitude, latitude, [altitude]
              getRadius: d => $scope.circleRadius,  // returns radius in meters
              getFillColor: d => [0, 0, 255]           // returns R, G, B, [A] in 0-255 range
            });
            $scope.scatterLayer.setProps(
              {
                layers: [deckGLScatterplot],
                viewState:
                  {
                    latitude: $scope.map.getCenter().lat,
                    longitude: $scope.map.getCenter().lng,
                    zoom: $scope.map.getZoom() - 1,
                    bearing: 0,
                    pitch: 0
                  }
              });
            console.log("==== view state for deck.lg ====");
            console.log("latitude: " + $scope.map.getCenter().lat);
            console.log("longitude: " + $scope.map.getCenter().lng);
            console.log("zoom: " + ($scope.map.getZoom() - 1));
            console.log("bearing: " + 0);
            console.log("pitch: " + 0);
            break;
        }
      }

      // timing for rendering
      let renderEnd = performance.now();
      let renderTime = (renderEnd - $scope.renderStart) / 1000.0; // seconds
      console.log("renderTime: " + renderTime + " seconds.");
      if ($scope.timeActions) {
        $scope.actionTime.renderTime = renderTime;
        $scope.actionTimings.push($scope.actionTime);
      }
      if ($scope.replaying) {
        moduleManager.publishEvent(moduleManager.EVENT.FINISH_ACTION, {});
      }
    };

    $scope.encodeColor = function (value) {
      let h = 100 - value * 100;
      let s = 60 + value * 30;
      return "hsl(" + h + ", " + s + "%, 50%)";
    };

    /** middleware mode */
    $scope.createFixedSizedClusterIcon = function(feature, latlng) {
      if (feature.properties.point_count === 0) return L.circleMarker(latlng, {radius: 2, fillColor: 'blue', fillOpacity: 0.9});

      const zoom_shift = $scope.zoomShift;
      const iconSize = 2 * $scope.radius / Math.pow(2, zoom_shift);
      const count = feature.properties.point_count;
      const size =
        count < 100 ? 'small' :
          count < 1000 ? 'medium' : 'large';
      const icon = L.divIcon({
        html: `<div><span>${feature.properties.point_count_abbreviated}</span></div>`,
        className: `marker-cluster-${zoom_shift} marker-cluster-${size}`,
        //iconSize: L.point(40, 40)
        iconSize: L.point(iconSize, iconSize)
      });

      return L.marker(latlng, {icon: icon, title: feature.properties.id, alt: feature.properties.id});
    };

    $scope.createVariedSizedClusterIcon = function(feature, latlng) {
      let circleRadius = $scope.circleRadius;
      if ($scope.scaleCircleRadius) {
        let zoom_shift = feature.properties.zoom < 25 ?
          feature.properties.zoom - $scope.map.getZoom() : $scope.zoomShift;
        circleRadius = $scope.circleRadius / Math.pow(2, zoom_shift);
        if (feature.properties.diameter >= 0.0) {
          circleRadius = feature.properties.diameter * $scope.circleRadius / $scope.radiuses[$scope.map.getZoom()];
        }
        //circleRadius = circleRadius < 1.5 * $scope.query.pixels? 1.5 * $scope.query.pixels: circleRadius;
        circleRadius = circleRadius < 1? 1: circleRadius;
      }

      let markerRadius = circleRadius * 0.7;
      let markerEdge = circleRadius * 0.3;

      const markerColor = $scope.colorEncoding? $scope.encodeColor(Math.log((feature.properties.point_count - 100) < 1 ?
          1 : (feature.properties.point_count - 100)) / Math.log($scope.pointsCount)) : 'blue';

      return L.circleMarker(latlng, {
        title: feature.properties.id,
        alt: feature.properties.id,
        radius: markerRadius,
        color: markerColor,
        opacity: 0.3,
        fillColor: markerColor,
        fillOpacity: 0.7,
        weight: markerEdge
      });
    };

    $scope.cleanClusterLayer = function () {
      if($scope.clusterLayer) {
        $scope.clusterLayer.clearLayers();
        $scope.map.removeLayer($scope.clusterLayer);
        $scope.clusterLayer = null;
      }
    };

    $scope.cleanHeatLayer = function () {
      if ($scope.heatLayer) {
        $scope.map.removeLayer($scope.heatLayer);
        $scope.heatLayer = null;
      }
    };

    $scope.cleanScatterLayer = function () {
      if ($scope.scatterLayer) {
        if ($scope.scatterType === "deck-gl") {
          $scope.scatterLayer.setProps({layers: []});
        }
        else {
          $scope.map.removeLayer($scope.scatterLayer);
        }
        $scope.scatterLayer = null;
      }
    };

    /** middleware mode */
    $scope.cleanMWLayers = function() {
      $scope.cleanClusterLayer();
      $scope.cleanHeatLayer();
      $scope.cleanScatterLayer();
    };

    /** frontend mode */
    $scope.cleanFELayers = function() {
      $scope.cleanClusterLayer();
      $scope.cleanHeatLayer();
      $scope.cleanScatterLayer();
    };

    /** frontend mode */
    $scope.drawFEClusterLayer = function (data) {
      // initialize the cluster layer
      if (!$scope.clusterLayer) {
        $scope.clusterLayer = L.markerClusterGroup({maxClusterRadius: 40, chunkedLoading: true });
        $scope.rawData = [];
      }

      // update the cluster layer
      if (data.length > 0) {
        console.log("[marker-cluster] drawing clusters size = " + data.length);
        let markersList = [];
        let start = performance.now();
        for (let i = 0; i < data.length; i ++) {
          $scope.rawData.push(data[i]);
          let p = data[i];
          let title = "" + p[2];
          let marker = L.marker(L.latLng(p[0], p[1]), { title: title });
          markersList.push(marker);
        }
        $scope.clusterLayer.addLayers(markersList);
        let end = performance.now();
        $scope.timings.push((end - start) / 1000.0);
        console.log("Until now, the clustering timings of [marker-cluster] for keyword \"" + $scope.query.keyword + "\" are: ");
        console.log($scope.timings);
        $scope.map.removeLayer($scope.clusterLayer);
        $scope.map.addLayer($scope.clusterLayer);
      }
    };

    /** frontend mode */
    $scope.drawFEHeatLayer = function(data) {
      // initialize the heat layer
      if (!$scope.heatLayer) {
        let circleRadius = $scope.circleRadius * 0.7;
        $scope.heatLayer = L.heatLayer([], {radius: circleRadius}).addTo($scope.map);
        $scope.rawData = [];
      }

      // update the heat layer
      if (data.length > 0) {
        for (let i = 0; i < data.length; i ++) {
          $scope.rawData.push(data[i]); // [lng, lat, id]
        }
        console.log("[Frontend - heatmap] drawing points size = " + $scope.rawData.length);
        let start = performance.now();
        // construct consumable points array for heat layer
        let points = [];
        for (let i = 0; i < $scope.rawData.length; i ++) {
          let point = $scope.rawData[i];
          points.push([point[0], point[1], 10]);
        }
        // redraw heat layer
        $scope.heatLayer.setLatLngs(points);
        $scope.heatLayer.redraw();
        let end = performance.now();
        console.log("[Frontend - heatmap] takes " + ((end - start) / 1000.0) + " seconds.");
      }
    };

    /** frontend mode */
    $scope.drawFEScatterLayer = function(data) {
      // initialize the scatter layer
      if (!$scope.scatterLayer) {
        let circleRadius = $scope.circleRadius;
        switch ($scope.scatterType) {
          case "gl-pixel":
            $scope.scatterLayer = new WebGLPointLayer({renderMode: "pixel"}); // renderMode: raster / pixel
            $scope.scatterLayer.setPointSize(2 * circleRadius);
            $scope.scatterLayer.setPointColor(0, 0, 255);
            break;
          case "gl-raster":
            $scope.scatterLayer = new WebGLPointLayer({renderMode: "raster"}); // renderMode: raster / pixel
            $scope.scatterLayer.setPointSize(2 * circleRadius);
            $scope.scatterLayer.setPointColor(0, 0, 255);
            break;
          case "leaflet":
            $scope.scatterLayer = L.TileLayer.maskCanvas({
              radius: circleRadius,  // radius in pixels or in meters (see useAbsoluteRadius)
              useAbsoluteRadius: false,  // true: r in meters, false: r in pixels
              color: 'blue',  // the color of the layer
              opacity: 1.0,  // opacity of the not covered area
              noMask: true//,  // true results in normal (filled) circled, instead masked circles
              //lineColor: 'blue'   // color of the circle outline if noMask is true
            });
            break;
        }
        $scope.map.addLayer($scope.scatterLayer);
        $scope.rawData = [];
      }

      // update the scatter layer
      if (data.length > 0) {
        if ($scope.rawData.length == 0) {
          $scope.rawData = data; // [lng, lat, id]
        }
        else {
          for (let i = 0; i < data.length; i ++) {
            $scope.rawData.push(data[i]); // [lng, lat, id]
          }
        }
        let start = performance.now();
        // construct consumable points array for scatter layer
        let points = [];
        switch ($scope.scatterType) {
          case "gl-pixel":
          case "gl-raster":
            console.log("[Frontend - scatter-plot] drawing points size = " + data.length);
            for (let i = 0; i < data.length; i ++) {
              let point = data[i];
              points.push([point[0], point[1], point[2]]);
            }
            break;
          case "leaflet":
            console.log("[Frontend - scatter-plot] drawing points size = " + $scope.rawData.length);
            for (let i = 0; i < $scope.rawData.length; i ++) {
              let point = $scope.rawData[i];
              points.push([point[0], point[1], point[2]]);
            }
            break;
        }
        let end = performance.now();
        console.log("[Frontend - scatter-plot] transforming data takes " + ((end - start) / 1000.0) + " seconds.");
        start = performance.now();
        // redraw scatter layer
        switch ($scope.scatterType) {
          case "gl-pixel":
          case "gl-raster":
            $scope.scatterLayer.appendData(points);
            break;
          case "leaflet":
            $scope.scatterLayer.setData(points);
            break;
        }
        end = performance.now();
        console.log("[Frontend - scatter-plot] rendering takes " + ((end - start) / 1000.0) + " seconds.");
      }
    };
  })
  .directive("map", function () {
    return {
      restrict: 'E',
      scope: {
        lat: "=",
        lng: "=",
        zoom: "="
      },
      controller: 'MapCtrl',
      template:[
        '<leaflet lf-center="center" tiles="tiles" events="events" controls="controls" width="100%" height="100%" ng-init="init()"></leaflet>'
      ].join('')
    };
  });


angular.module("clustermap.map")
  .controller('CountCtrl', function($scope, moduleManager) {
    $scope.resultCount = "";

    moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_RESULT_COUNT, function(e) {
      if (e.resultCount) {
        $scope.resultCount = e.resultCount + ": " + e.pointsCount;
      }
      else {
        $scope.resultCount = e.pointsCount;
      }
    })
  });