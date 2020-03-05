angular.module("clustermap.searchbar", ["clustermap.common"])
    .controller("SearchCtrl", function ($scope, moduleManager) {
        $scope.disableSearchButton = true;

        $scope.search = function () {
            if ($scope.keyword && $scope.keyword.trim().length > 0) {
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_SEARCH_KEYWORD,
                    {
                      keyword: $scope.keyword,
                      order: $scope.order,
                      algorithm: $scope.algorithm,
                      indexType: $scope.indexType,
                      zoom: $scope.zoom,
                      analysis: $scope.analysis,
                      treeCut: $scope.treeCut,
                      measure: $scope.measure,
                      pixels: $scope.pixels,
                      bipartite: $scope.bipartite
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

        $scope.orders = ["original", "reverse", "spatial", "spatial-reverse"];
        $scope.algorithms = ["SuperCluster", "SuperClusterInBatch", "iSuperCluster",
          "AiSuperCluster", "BiSuperCluster", "LBiSuperCluster", "GQuadTreeAggregator", "QuadTreeAggregator",
          "DataExplorer", "DataAggregator", "SBiSuperCluster"];
        $scope.indexTypes = ["KDTree", "GridIndex"];
        $scope.zooms = [4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17];
        $scope.analysises = ["", "rand-index", "adjusted-rand-index"];
        $scope.treeCut = false;
        $scope.measures = ["max", "min", "avg"];
        $scope.pixelsOptions = [0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2, 3, 4, 5, 10, 15, 20];
        $scope.bipartite = false;
        $scope.mwVisualizationTypes = ["cluster", "scatter", "heat"];
        $scope.feVisualizationTypes = ["cluster", "scatter", "heat"];
        $scope.scatterTypes = ["gl-pixel", "gl-raster", "leaflet", "deck-gl"];
        $scope.recording = false;
        $scope.replaying = false;

        /** Left side controls */
        //Zoom Shift Select
        $scope.selectZoomShift = document.createElement("select");
        $scope.selectZoomShift.title = "zoomShift";
        $scope.selectZoomShift.style.position = 'fixed';
        $scope.selectZoomShift.style.top = '90px';
        $scope.selectZoomShift.style.left = '8px';
        for (let i = 0; i <= 6; i ++) {
          let option = document.createElement("option");
          option.text = ""+ i;
          $scope.selectZoomShift.add(option);
        }
        $scope.selectZoomShift.value = "0";
        document.body.appendChild($scope.selectZoomShift);
        $scope.selectZoomShift.addEventListener("change", function () {
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_ZOOM_SHIFT,
            {zoomShift: $scope.selectZoomShift.value});
        });
        $scope.selectZoomShiftLabel = document.createElement("label");
        $scope.selectZoomShiftLabel.innerHTML = "Zoom Shift";
        $scope.selectZoomShiftLabel.htmlFor ="zoomShift";
        $scope.selectZoomShiftLabel.style.position = 'fixed';
        $scope.selectZoomShiftLabel.style.top = '90px';
        $scope.selectZoomShiftLabel.style.left = '45px';
        document.body.appendChild($scope.selectZoomShiftLabel);

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
                $scope.selectCircleRadius.value = "1";
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_CIRCLE_RADIUS,
                  {circleRadius: $scope.selectCircleRadius.value});
                break;
              case "heat":
                $scope.selectCircleRadius.value = "20";
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_CIRCLE_RADIUS,
                  {circleRadius: $scope.selectCircleRadius.value});
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
              case "cluster":
                $scope.checkboxNumber.checked = true;
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_NUMBER_IN_CIRCLE,
                  {numberInCircle: $scope.checkboxNumber.checked});
                $scope.treeCut = false;
                break;
              case "scatter":
                $scope.checkboxNumber.checked = false;
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_NUMBER_IN_CIRCLE,
                  {numberInCircle: $scope.checkboxNumber.checked});
                $scope.checkboxColor.checked = false;
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_COLOR_ENCODING,
                  {colorEncoding: $scope.checkboxColor.checked});
                $scope.selectCircleRadius.value = "1";
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_CIRCLE_RADIUS,
                  {circleRadius: $scope.selectCircleRadius.value});
                $scope.checkboxScaleCircleRadius.checked = false;
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_SCALE_CIRCLE_RADIUS,
                  {scaleCircleRadius: $scope.checkboxScaleCircleRadius.checked});
                $scope.treeCut = true;
                $scope.measure = "max";
                $scope.pixels = 1.0;
                $scope.bipartite = false;
                break;
              case "heat":
                $scope.checkboxNumber.checked = false;
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_NUMBER_IN_CIRCLE,
                  {numberInCircle: $scope.checkboxNumber.checked});
                $scope.checkboxColor.checked = false;
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_COLOR_ENCODING,
                  {colorEncoding: $scope.checkboxColor.checked});
                $scope.selectCircleRadius.value = "5";
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_CIRCLE_RADIUS,
                  {circleRadius: $scope.selectCircleRadius.value});
                $scope.checkboxScaleCircleRadius.checked = false;
                moduleManager.publishEvent(moduleManager.EVENT.CHANGE_SCALE_CIRCLE_RADIUS,
                  {scaleCircleRadius: $scope.checkboxScaleCircleRadius.checked});
                $scope.treeCut = true;
                $scope.measure = "max";
                $scope.pixels = 2.0;
                $scope.bipartite = false;
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

        // Checkbox for numbers in circles
        $scope.checkboxNumber = document.createElement("input");
        $scope.checkboxNumber.type = "checkbox";
        $scope.checkboxNumber.id = "numberInCircle";
        $scope.checkboxNumber.name = "numberInCircle";
        $scope.checkboxNumber.checked = true;
        $scope.checkboxNumber.style.position = 'fixed';
        $scope.checkboxNumber.style.top = '140px';
        $scope.checkboxNumber.style.left = '8px';
        document.body.appendChild($scope.checkboxNumber);
        $scope.checkboxNumber.addEventListener("change", function() {
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_NUMBER_IN_CIRCLE,
            {numberInCircle: this.checked});
        });
        $scope.checkboxNumberLabel = document.createElement("label");
        $scope.checkboxNumberLabel.innerHTML = "Number in Circle";
        $scope.checkboxNumberLabel.htmlFor = "numberInCircle";
        $scope.checkboxNumberLabel.style.position = 'fixed';
        $scope.checkboxNumberLabel.style.top = '140px';
        $scope.checkboxNumberLabel.style.left = '24px';
        document.body.appendChild($scope.checkboxNumberLabel);

        // Checkbox for color encoding
        $scope.checkboxColor = document.createElement("input");
        $scope.checkboxColor.type = "checkbox";
        $scope.checkboxColor.id = "colorEncoding";
        $scope.checkboxColor.name = "colorEncoding";
        $scope.checkboxColor.checked = true;
        $scope.checkboxColor.style.position = 'fixed';
        $scope.checkboxColor.style.top = '155px';
        $scope.checkboxColor.style.left = '8px';
        document.body.appendChild($scope.checkboxColor);
        $scope.checkboxColor.addEventListener("change", function() {
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_COLOR_ENCODING,
            {colorEncoding: this.checked});
        });
        $scope.checkboxColorLabel = document.createElement("label");
        $scope.checkboxColorLabel.innerHTML = "Color Encoding";
        $scope.checkboxColorLabel.htmlFor = "colorEncoding";
        $scope.checkboxColorLabel.style.position = 'fixed';
        $scope.checkboxColorLabel.style.top = '155px';
        $scope.checkboxColorLabel.style.left = '24px';
        document.body.appendChild($scope.checkboxColorLabel);

        // Circle Radius Select
        $scope.selectCircleRadius = document.createElement("select");
        $scope.selectCircleRadius.title = "circleRadius";
        $scope.selectCircleRadius.style.position = 'fixed';
        $scope.selectCircleRadius.style.top = '175px';
        $scope.selectCircleRadius.style.left = '8px';
        for (let i = 20; i >=5; i -= 5) {
          let option = document.createElement("option");
          option.text = "" + i;
          $scope.selectCircleRadius.add(option);
        }
        for (let i = 4; i >=1 ; i -= 0.5) {
          let option = document.createElement("option");
          option.text = "" + i;
          $scope.selectCircleRadius.add(option);
        }
        let option = document.createElement("option");
        option.text = "0.5";
        $scope.selectCircleRadius.add(option);
        option = document.createElement("option");
        option.text = "0.25";
        $scope.selectCircleRadius.add(option);

        $scope.selectCircleRadius.value = "20";
        document.body.appendChild($scope.selectCircleRadius);
        $scope.selectCircleRadius.addEventListener("change", function () {
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_CIRCLE_RADIUS,
            {circleRadius: $scope.selectCircleRadius.value});
        });
        $scope.selectCircleRadiusLabel = document.createElement("label");
        $scope.selectCircleRadiusLabel.innerHTML = "Circle Radius";
        $scope.selectCircleRadiusLabel.htmlFor ="circleRadius";
        $scope.selectCircleRadiusLabel.style.position = 'fixed';
        $scope.selectCircleRadiusLabel.style.top = '175px';
        $scope.selectCircleRadiusLabel.style.left = '60px';
        document.body.appendChild($scope.selectCircleRadiusLabel);

        // Checkbox for scale circle radius
        $scope.checkboxScaleCircleRadius = document.createElement("input");
        $scope.checkboxScaleCircleRadius.type = "checkbox";
        $scope.checkboxScaleCircleRadius.id = "scaleCircleRadius";
        $scope.checkboxScaleCircleRadius.name = "scaleCircleRadius";
        $scope.checkboxScaleCircleRadius.checked = false;
        $scope.checkboxScaleCircleRadius.style.position = 'fixed';
        $scope.checkboxScaleCircleRadius.style.top = '195px';
        $scope.checkboxScaleCircleRadius.style.left = '8px';
        document.body.appendChild($scope.checkboxScaleCircleRadius);
        $scope.checkboxScaleCircleRadius.addEventListener("change", function() {
          moduleManager.publishEvent(moduleManager.EVENT.CHANGE_SCALE_CIRCLE_RADIUS,
            {scaleCircleRadius: this.checked});
        });
        $scope.checkboxScaleCircleRadiusLabel = document.createElement("label");
        $scope.checkboxScaleCircleRadiusLabel.innerHTML = "Scale Circle Radius";
        $scope.checkboxScaleCircleRadiusLabel.htmlFor = "scaleCircleRadius";
        $scope.checkboxScaleCircleRadiusLabel.style.position = 'fixed';
        $scope.checkboxScaleCircleRadiusLabel.style.top = '195px';
        $scope.checkboxScaleCircleRadiusLabel.style.left = '24px';
        document.body.appendChild($scope.checkboxScaleCircleRadiusLabel);

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
                '<label for="order">Order</label>&nbsp;<select id="order" ng-model="order" ng-options="x for x in orders" ng-init="order = orders[0]"></select>&nbsp;',
                '<label for="algorithm">Algorithm</label>&nbsp;<select id="algorithm" ng-model="algorithm" ng-options="x for x in algorithms" ng-init="algorithm = algorithms[5]"></select>&nbsp;',
                '<label for="indexType">IndexType</label>&nbsp;<select id="indexType" ng-model="indexType" ng-options="x for x in indexTypes" ng-init="indexType = indexTypes[0]"></select>&nbsp;',
                '<label for="zoom">Zoom</label>&nbsp;<select id="zoom" ng-model="zoom" ng-options="x for x in zooms" ng-init="zoom = zooms[0]"></select>&nbsp;',
                '<label for="analysis">Analysis</label>&nbsp;<select id="analysis" ng-model="analysis" ng-options="x for x in analysises" ng-init="analysis = analysises[0]"></select>&nbsp;',
                '<label for="treeCut">Tree-Cut</label>&nbsp;<input id="treeCut" type="checkbox" ng-model="treeCut"></input>&nbsp;',
                '<label for="measure">Measure</label>&nbsp;<select id="measure" ng-model="measure" ng-options="x for x in measures" ng-init="measure = measures[0]"></select>&nbsp;',
                '<label for="pixels">Pixels</label>&nbsp;<select id="pixels" ng-model="pixels" ng-options="x for x in pixelsOptions" ng-init="pixels = pixelsOptions[6]"></select>&nbsp;',
                '<label for="bipartite">Bipartite</label>&nbsp;<input id="bipartite" type="checkbox" ng-model="bipartite"></input>&nbsp;'
            ].join('')
        };
    });