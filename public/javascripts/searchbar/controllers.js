angular.module("clustermap.searchbar", ["clustermap.common"])
    .controller("SearchCtrl", function ($scope, moduleManager) {
        $scope.disableSearchButton = true;

        $scope.search = function () {
            if ($scope.keyword && $scope.keyword.trim().length > 0) {
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_SEARCH_KEYWORD,
                    {
                      keyword: $scope.keyword,
                      algorithm: $scope.algorithm
                    });
            }
            // else {
            //   moduleManager.publishEvent(moduleManager.EVENT.CHANGE_SEARCH_KEYWORD,
            //     {
            //       keyword: "%",
            //       order: $scope.order,
            //       algorithm: $scope.algorithm,
            //       indexType: $scope.indexType,
            //       zoom: $scope.zoom,
            //       analysis: $scope.analysis,
            //       treeCut: $scope.treeCut,
            //       measure: $scope.measure,
            //       pixels: $scope.pixels,
            //       bipartite: $scope.bipartite
            //     });
            // }
        };

        moduleManager.subscribeEvent(moduleManager.EVENT.WS_READY, function(e) {
            $scope.disableSearchButton = false;
        });

        $scope.algorithms = ["DataExplorer", "DataAggregator", "QuadTree", "GQuadTree", "RAQuadTree", "RAQuadTreeSnap"];
        $scope.mwVisualizationTypes = ["scatter", "heat"];
        $scope.feVisualizationTypes = ["scatter", "heat"];
        $scope.scatterTypes = ["gl-pixel", "gl-raster", "leaflet", "deck-gl"];
        $scope.recording = false;
        $scope.replaying = false;

        /** Left side controls */
        /** Frontend mode */
        // Select for Scatter Types under Frontend Scatter mode
        $scope.addSelectFEScatterTypes = function() {
          // Select for frontend-scatter mode scatter types
          $scope.selectFEScatterTypes = document.createElement("select");
          $scope.selectFEScatterTypes.id = "feScatterTypes";
          $scope.selectFEScatterTypes.title = "feScatterTypes";
          $scope.selectFEScatterTypes.style.position = 'fixed';
          $scope.selectFEScatterTypes.style.top = '110px';
          $scope.selectFEScatterTypes.style.left = '155px';
          for (let i = 0; i < $scope.scatterTypes.length; i ++) {
            let option = document.createElement("option");
            option.text = $scope.scatterTypes[i];
            $scope.selectFEScatterTypes.add(option);
          }
          $scope.selectFEScatterTypes.value = $scope.scatterTypes[0];
          document.body.appendChild($scope.selectFEScatterTypes);
          $scope.selectFEScatterTypes.addEventListener("change", function () {
            moduleManager.publishEvent(moduleManager.EVENT.CHANGE_SCATTER_TYPE,
              {scatterType: $scope.selectFEScatterTypes.value});
          });
        };
        // only show it when mode is "frontend-scatter"
        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_FE_VISUALIZATION_TYPE, function(e) {
          if (e.feVisualizationType === "scatter") {
            // if select element does not exist, create it
            if (document.getElementById("feScatterTypes") === null) {
              $scope.addSelectFEScatterTypes();
            }
          }
          else {
            // if select element exists, remove it
            if (document.getElementById("feScatterTypes")) {
              document.body.removeChild($scope.selectFEScatterTypes);
              $scope.selectFEScatterTypes = null;
            }
          }
        });

        // Select for Visualization Types under Frontend mode
        $scope.addSelectFEVisualizationTypes = function() {
          // Select for frontend mode visualization types
          $scope.selectFEVisualizationTypes = document.createElement("select");
          $scope.selectFEVisualizationTypes.id = "feVisualizationTypes";
          $scope.selectFEVisualizationTypes.title = "feVisualizationTypes";
          $scope.selectFEVisualizationTypes.style.position = 'fixed';
          $scope.selectFEVisualizationTypes.style.top = '110px';
          $scope.selectFEVisualizationTypes.style.left = '85px';
          for (let i = 0; i < $scope.feVisualizationTypes.length; i ++) {
            let option = document.createElement("option");
            option.text = $scope.feVisualizationTypes[i];
            $scope.selectFEVisualizationTypes.add(option);
          }
          $scope.selectFEVisualizationTypes.value = $scope.feVisualizationTypes[0];
          document.body.appendChild($scope.selectFEVisualizationTypes);
          $scope.selectFEVisualizationTypes.addEventListener("change", function () {
            moduleManager.publishEvent(moduleManager.EVENT.CHANGE_FE_VISUALIZATION_TYPE,
              {feVisualizationType: $scope.selectFEVisualizationTypes.value});
            switch ($scope.selectFEVisualizationTypes.value) {
              case "scatter":
                $scope.selectPointRadius.value = "1";
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_POINT_RADIUS,
                  {pointRadius: $scope.selectPointRadius.value});
                break;
              case "heat":
                $scope.selectPointRadius.value = "20";
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_POINT_RADIUS,
                  {pointRadius: $scope.selectPointRadius.value});
                break;
            }
          });
        };
        // only show it when mode is "frontend"
        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_MODE, function(e) {
          if (e.mode === "frontend") {
            // if select element does not exist, create it
            if (document.getElementById("feVisualizationTypes") === null) {
              $scope.addSelectFEVisualizationTypes();
            }
          }
          else {
            // if select element exists, remove it
            if (document.getElementById("feVisualizationTypes")) {
              document.body.removeChild($scope.selectFEVisualizationTypes);
              $scope.selectFEVisualizationTypes = null;
            }
            // if select frontend scatter types exists, remove it
            if (document.getElementById("feScatterTypes")) {
              document.body.removeChild($scope.selectFEScatterTypes);
              $scope.selectFEScatterTypes = null;
            }
          }
        });

        // Frontend mode radio
        $scope.radioFrontend = document.createElement("input");
        $scope.radioFrontend.type = "radio";
        $scope.radioFrontend.id = "frontend";
        $scope.radioFrontend.name = "mode";
        $scope.radioFrontend.value = "frontend";
        $scope.radioFrontend.style.position = 'fixed';
        $scope.radioFrontend.style.top = '110px';
        $scope.radioFrontend.style.left = '8px';
        document.body.appendChild($scope.radioFrontend);
        $scope.radioFrontend.addEventListener("click", function() {
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_MODE,
            {mode: "frontend"});
        });
        $scope.radioFrontendLabel = document.createElement("label");
        $scope.radioFrontendLabel.innerHTML = "Frontend";
        $scope.radioFrontendLabel.htmlFor ="frontend";
        $scope.radioFrontendLabel.style.position = 'fixed';
        $scope.radioFrontendLabel.style.top = '110px';
        $scope.radioFrontendLabel.style.left = '24px';
        document.body.appendChild($scope.radioFrontendLabel);

        /** Middleware mode */
        // Select for Scatter Types under Middleware Scatter mode
        $scope.addSelectMWScatterTypes = function() {
          // Select for middleware-scatter mode scatter types
          $scope.selectMWScatterTypes = document.createElement("select");
          $scope.selectMWScatterTypes.id = "mwScatterTypes";
          $scope.selectMWScatterTypes.title = "mwScatterTypes";
          $scope.selectMWScatterTypes.style.position = 'fixed';
          $scope.selectMWScatterTypes.style.top = '125px';
          $scope.selectMWScatterTypes.style.left = '175px';
          for (let i = 0; i < $scope.scatterTypes.length; i ++) {
            let option = document.createElement("option");
            option.text = $scope.scatterTypes[i];
            $scope.selectMWScatterTypes.add(option);
          }
          $scope.selectMWScatterTypes.value = $scope.scatterTypes[0];
          document.body.appendChild($scope.selectMWScatterTypes);
          $scope.selectMWScatterTypes.addEventListener("change", function () {
            moduleManager.publishEvent(moduleManager.EVENT.CHANGE_SCATTER_TYPE,
              {scatterType: $scope.selectMWScatterTypes.value});
          });
        };
        // by default show it, since by default mode = "middleware", mwVisualizationType = "scatter";
        $scope.addSelectMWScatterTypes();
        // only show it when mode is "middleware-scatter"
        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_MW_VISUALIZATION_TYPE, function(e) {
          if (e.mwVisualizationType === "scatter") {
            // if select element does not exist, create it
            if (document.getElementById("mwScatterTypes") === null) {
              $scope.addSelectMWScatterTypes();
            }
          }
          else {
            // if select element exists, remove it
            if (document.getElementById("mwScatterTypes")) {
              document.body.removeChild($scope.selectMWScatterTypes);
              $scope.selectMWScatterTypes = null;
            }
          }
        });
        // Select for Visualization Types under Middleware mode
        $scope.addSelectMWVisualizationTypes = function() {
          // Select for middleware mode visualization types
          $scope.selectMWVisualizationTypes = document.createElement("select");
          $scope.selectMWVisualizationTypes.id = "mwVisualizationTypes";
          $scope.selectMWVisualizationTypes.title = "mwVisualizationTypes";
          $scope.selectMWVisualizationTypes.style.position = 'fixed';
          $scope.selectMWVisualizationTypes.style.top = '125px';
          $scope.selectMWVisualizationTypes.style.left = '105px';
          for (let i = 0; i < $scope.mwVisualizationTypes.length; i ++) {
            let option = document.createElement("option");
            option.text = $scope.mwVisualizationTypes[i];
            $scope.selectMWVisualizationTypes.add(option);
          }
          $scope.selectMWVisualizationTypes.value = $scope.mwVisualizationTypes[0];
          document.body.appendChild($scope.selectMWVisualizationTypes);
          $scope.selectMWVisualizationTypes.addEventListener("change", function () {
            moduleManager.publishEvent(moduleManager.EVENT.CHANGE_MW_VISUALIZATION_TYPE,
              {mwVisualizationType: $scope.selectMWVisualizationTypes.value});
            switch ($scope.selectMWVisualizationTypes.value) {
              case "scatter":
                $scope.selectPointRadius.value = "1";
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_POINT_RADIUS,
                  {pointRadius: $scope.selectPointRadius.value});
                break;
              case "heat":
                $scope.selectPointRadius.value = "5";
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_POINT_RADIUS,
                  {pointRadius: $scope.selectPointRadius.value});
                break;
            }
            $scope.search();
          });
        };
        // by default show it, since by default mode = "middleware";
        $scope.addSelectMWVisualizationTypes();
        // only show it when mode is "middleware"
        moduleManager.subscribeEvent(moduleManager.EVENT.CHANGE_MODE, function(e) {
          if (e.mode === "middleware") {
            // if select element does not exist, create it
            if (document.getElementById("mwVisualizationTypes") === null) {
              $scope.addSelectMWVisualizationTypes();
            }
          }
          else {
            // if select element exists, remove it
            if (document.getElementById("mwVisualizationTypes")) {
              document.body.removeChild($scope.selectMWVisualizationTypes);
              $scope.selectMWVisualizationTypes = null;
            }
            // if select middleware scatter types exists, remove it
            if (document.getElementById("mwScatterTypes")) {
              document.body.removeChild($scope.selectMWScatterTypes);
              $scope.selectMWScatterTypes = null;
            }
          }
        });

        // Middleware mode radio
        $scope.radioMiddleware = document.createElement("input");
        $scope.radioMiddleware.type = "radio";
        $scope.radioMiddleware.id = "middleware";
        $scope.radioMiddleware.name = "mode";
        $scope.radioMiddleware.value = "middleware";
        $scope.radioMiddleware.checked = true;
        $scope.radioMiddleware.style.position = 'fixed';
        $scope.radioMiddleware.style.top = '125px';
        $scope.radioMiddleware.style.left = '8px';
        document.body.appendChild($scope.radioMiddleware);
        $scope.radioMiddleware.addEventListener("click", function() {
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_MODE,
            {mode: "middleware"});
        });
        $scope.radioMiddlewareLabel = document.createElement("label");
        $scope.radioMiddlewareLabel.innerHTML = "Middleware";
        $scope.radioMiddlewareLabel.htmlFor = "middleware";
        $scope.radioMiddlewareLabel.style.position = 'fixed';
        $scope.radioMiddlewareLabel.style.top = '125px';
        $scope.radioMiddlewareLabel.style.left = '24px';
        document.body.appendChild($scope.radioMiddlewareLabel);

        // Point Radius Select
        $scope.selectPointRadius = document.createElement("select");
        $scope.selectPointRadius.title = "pointRadius";
        $scope.selectPointRadius.style.position = 'fixed';
        $scope.selectPointRadius.style.top = '150px';
        $scope.selectPointRadius.style.left = '8px';
        for (let i = 0.5; i <=5 ; i += 0.5) {
          let option = document.createElement("option");
          option.text = "" + i;
          $scope.selectPointRadius.add(option);
        }

        $scope.selectPointRadius.value = "1";
        document.body.appendChild($scope.selectPointRadius);
        $scope.selectPointRadius.addEventListener("change", function () {
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_POINT_RADIUS,
            {pointRadius: $scope.selectPointRadius.value});
        });
        $scope.selectPointRadiusLabel = document.createElement("label");
        $scope.selectPointRadiusLabel.innerHTML = "Point Radius";
        $scope.selectPointRadiusLabel.htmlFor ="pointRadius";
        $scope.selectPointRadiusLabel.style.position = 'fixed';
        $scope.selectPointRadiusLabel.style.top = '150px';
        $scope.selectPointRadiusLabel.style.left = '60px';
        document.body.appendChild($scope.selectPointRadiusLabel);

        // Opacity Select
        $scope.selectOpacity = document.createElement("select");
        $scope.selectOpacity.title = "opacity";
        $scope.selectOpacity.style.position = 'fixed';
        $scope.selectOpacity.style.top = '175px';
        $scope.selectOpacity.style.left = '8px';
        for (let i = 1; i <= 9; i ++) {
          let option = document.createElement("option");
          option.text = "0." + i;
          $scope.selectOpacity.add(option);
        }
        let option = document.createElement("option");
        option.text = "1.0";
        $scope.selectOpacity.add(option);

        $scope.selectOpacity.value = "1.0";
        document.body.appendChild($scope.selectOpacity);
        $scope.selectOpacity.addEventListener("change", function () {
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_OPACITY,
            {opacity: $scope.selectOpacity.value});
        });
        $scope.selectOpacityLabel = document.createElement("label");
        $scope.selectOpacityLabel.innerHTML = "Opacity";
        $scope.selectOpacityLabel.htmlFor ="opacity";
        $scope.selectOpacityLabel.style.position = 'fixed';
        $scope.selectOpacityLabel.style.top = '175px';
        $scope.selectOpacityLabel.style.left = '60px';
        document.body.appendChild($scope.selectOpacityLabel);

        // Sample Percentage Select
        $scope.selectSamplePercentage = document.createElement("select");
        $scope.selectSamplePercentage.title = "SamplePercentage";
        $scope.selectSamplePercentage.style.position = 'fixed';
        $scope.selectSamplePercentage.style.top = '200px';
        $scope.selectSamplePercentage.style.left = '8px';

        for (let i = 100; i >= 15; i -= 5) {
          let option = document.createElement("option");
          option.text = "" + i;
          $scope.selectSamplePercentage.add(option);
        }
        for (let i = 10; i >= 0; i -= 1) {
          let option = document.createElement("option");
          option.text = "" + i;
          $scope.selectSamplePercentage.add(option);
        }

        $scope.selectSamplePercentage.value = "100";
        document.body.appendChild($scope.selectSamplePercentage);
        $scope.selectSamplePercentage.addEventListener("change", function () {
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_SAMPLE_PERCENTAGE,
            {samplePercentage: $scope.selectSamplePercentage.value});
        });
        $scope.selectSamplePercentageLabel = document.createElement("label");
        $scope.selectSamplePercentageLabel.innerHTML = "% Sample";
        $scope.selectSamplePercentageLabel.htmlFor ="SamplePercentage";
        $scope.selectSamplePercentageLabel.style.position = 'fixed';
        $scope.selectSamplePercentageLabel.style.top = '200px';
        $scope.selectSamplePercentageLabel.style.left = '60px';
        document.body.appendChild($scope.selectSamplePercentageLabel);

        // Error Select
        // $scope.selectError = document.createElement("select");
        // $scope.selectError.title = "Error";
        // $scope.selectError.style.position = 'fixed';
        // $scope.selectError.style.top = '225px';
        // $scope.selectError.style.left = '8px';
        // for (let i = 0; i <= 200; i += 10) {
        //   let option = document.createElement("option");
        //   option.text = "" + i;
        //   $scope.selectError.add(option);
        // }
        // for (let i = 250; i <= 10000; i += 50) {
        //   let option = document.createElement("option");
        //   option.text = "" + i;
        //   $scope.selectError.add(option);
        // }
        //
        // $scope.selectError.value = "0";
        // document.body.appendChild($scope.selectError);
        // $scope.selectError.addEventListener("change", function () {
        //   moduleManager.publishEvent(moduleManager.EVENT.CHANGE_ERROR,
        //     {error: $scope.selectError.value});
        // });
        // $scope.selectErrorLabel = document.createElement("label");
        // $scope.selectErrorLabel.innerHTML = "Error";
        // $scope.selectErrorLabel.htmlFor ="error";
        // $scope.selectErrorLabel.style.position = 'fixed';
        // $scope.selectErrorLabel.style.top = '225px';
        // $scope.selectErrorLabel.style.left = '75px';
        // document.body.appendChild($scope.selectErrorLabel);

        // Button for recording actions
        $scope.buttonRecord = document.createElement("button");
        $scope.buttonRecord.id = "record";
        $scope.buttonRecord.name = "record";
        $scope.buttonRecord.innerHTML = "record";
        $scope.buttonRecord.style.position = "fixed";
        $scope.buttonRecord.style.top = "9px";
        $scope.buttonRecord.style.left = "50px";
        $scope.buttonRecord.className = "record";
        document.body.appendChild($scope.buttonRecord);
        $scope.buttonRecord.addEventListener("click", function() {
          $scope.recording = !$scope.recording;
          console.log("[Button] recording? " + $scope.recording);
          $scope.buttonRecord.innerHTML = $scope.recording? "recording...": "record";
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_RECORDING,
            {recording: $scope.recording});
        });

        // Button for replaying actions
        $scope.buttonReplay = document.createElement("button");
        $scope.buttonReplay.id = "replay";
        $scope.buttonReplay.name = "replay";
        $scope.buttonReplay.innerHTML = "replay";
        $scope.buttonReplay.style.position = "fixed";
        $scope.buttonReplay.style.top = "36px";
        $scope.buttonReplay.style.left = "50px";
        $scope.buttonReplay.className = "record";
        document.body.appendChild($scope.buttonReplay);
        $scope.buttonReplay.addEventListener("click", function() {
          $scope.replaying = !$scope.replaying;
          console.log("[Button] replaying? " + $scope.replaying);
          $scope.buttonReplay.innerHTML = $scope.replaying? "replaying...": "replay";
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_REPLAYING,
            {replaying: $scope.replaying});
        });
        moduleManager.subscribeEvent(moduleManager.EVENT.FINISH_REPLAY, function(e) {
          $scope.replaying = false;
          console.log("[Button] replaying is done!");
          $scope.buttonReplay.innerHTML = "replay";
        });

        // Input for loading actions json file
        $scope.fileActions = document.createElement("input");
        $scope.fileActions.type = "file";
        $scope.fileActions.id = "fileActions";
        $scope.fileActions.style.position = "fixed";
        $scope.fileActions.style.top = "9px";
        $scope.fileActions.style.left = "140px";
        document.body.appendChild($scope.fileActions);
        // Input button loading actions json file
        $scope.buttonLoad = document.createElement("button");
        $scope.buttonLoad.id = "load";
        $scope.buttonLoad.name = "load";
        $scope.buttonLoad.innerHTML = "load";
        $scope.buttonLoad.style.position = "fixed";
        $scope.buttonLoad.style.top = "36px";
        $scope.buttonLoad.style.left = "140px";
        $scope.buttonLoad.className = "load";
        document.body.appendChild($scope.buttonLoad);
        $scope.buttonLoad.addEventListener("click", function() {
          if (typeof window.FileReader !== 'function') {
            alert("The file API isn't supported on this browser yet.");
            return;
          }
          let input = document.getElementById('fileActions');
          if (!input) {
            alert("Um, couldn't find the fileActions element.");
          }
          else if (!input.files) {
            alert("This browser doesn't seem to support the `files` property of file inputs.");
          }
          else if (!input.files[0]) {
            alert("Please select a file before clicking 'Load'");
          }
          else {
            let file = input.files[0];
            let fr = new FileReader();
            fr.onload = receivedText;
            fr.readAsText(file);
          }

          function receivedText(e) {
            let lines = e.target.result;
            let actions = JSON.parse(lines);
            // console.log("===== file loaded =====");
            // console.log(JSON.stringify(actions));
            moduleManager.publishEvent(moduleManager.EVENT.LOAD_ACTIONS, {actions: actions});
          }
        });
    })
    .directive("searchBar", function () {
        return {
            restrict: "E",
            controller: "SearchCtrl",
            template: [
                '<form class="form-inline" id="input-form" ng-submit="search()" >',
                '  <div class="input-group col-lg-12">',
                '    <label class="sr-only">Keywords</label>',
                '    <input type="text" style="width: 97%" class="form-control " id="keyword-textbox" placeholder="Search keywords, e.g. hurricane" ng-model="keyword"/>',
                '    <span class="input-group-btn">',
                '      <button type="submit" class="btn btn-primary" id="submit-button" ng-disabled="disableSearchButton">Submit</button>',
                '    </span>',
                '  </div>',
                '  <div id="myProgress" class="input-group col-lg-12" style="width: 69%">',
                '    <div id="myBar"></div>',
                '  </div>',
                '</form>',
                '<label for="algorithm">Algorithm</label>&nbsp;<select id="algorithm" ng-model="algorithm" ng-options="x for x in algorithms" ng-init="algorithm = algorithms[0]"></select>&nbsp;'
            ].join('')
        };
    });