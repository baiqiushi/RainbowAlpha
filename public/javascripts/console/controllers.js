angular.module("clustermap.console", ["clustermap.common", "vtortola.ng-terminal"])
  .controller("console", function ($scope, moduleManager) {

    setTimeout(function () {
      $scope.$broadcast('terminal-output', {
        output: true,
        text: ['Welcome to Cluster Viz Terminal!',
          "Please type 'help' to open a list of commands"],
        breakLine: true
      });
      $scope.$apply();
    }, 100);

    $scope.$on('terminal-input', function (e, consoleInput) {
      let cmd = consoleInput[0].command;
      console.log("terminal input: " + JSON.stringify(cmd));

      let tokens = cmd.split(" ");

      if (tokens[0] === "exit") {
        $("#console").slideToggle();
        return;
      }

      let request = $scope.validate(tokens);

      if(request === null) {
        return;
      }

      moduleManager.publishEvent(moduleManager.EVENT.CONSOLE_INPUT, request);
    });

    $scope.showHelp = function() {
      $scope.showHelpForCmd("default");
      $scope.showHelpForAnalysis("default");
    };

    moduleManager.subscribeEvent(moduleManager.EVENT.CONSOLE_OUTPUT, function(response) {
      $scope.$broadcast('terminal-output', {
        output: true,
        text: ["status: " + response.status, "result: " + JSON.stringify(response.result)],
      });
    });

    $scope.validate = function(tokens) {
      console.log("validating tokens = " + tokens);
      let type = tokens[0];
      switch (type) {
        case "query":
          // TODO - validate query commands
          break;
        case "load":
          // put "cmd" to the beginning of tokens
          tokens.unshift("cmd");
          return $scope.validate_cmd(tokens);
        case "cluster":
          // put "cmd" to the beginning of tokens
          tokens.unshift("cmd");
          return $scope.validate_cmd(tokens);
        case "cmd":
          return $scope.validate_cmd(tokens);
        case "distance":
          // put "analysis" to the beginning of tokens
          tokens.unshift("analysis");
          return $scope.validate_analysis(tokens);
        case "rand-index":
          // put "analysis" to the beginning of tokens
          tokens.unshift("analysis");
          return $scope.validate_analysis(tokens);
        case "analysis":
          return $scope.validate_analysis(tokens);
        default:
          $scope.showHelp();
          return null;
      }
    };

    $scope.showHelpForCmd = function(action) {
      switch (action) {
        case "load":
          $scope.$broadcast('terminal-output', {
            output: true,
            text: [
              "load [keyword]",
              "  load data into middleware for [keyword] query"]
          });
          break;
        case "cluster":
          $scope.$broadcast('terminal-output', {
            output: true,
            text: [
              "cluster [key] [order]",
              "  generate cluster in the middleware for current loaded data",
              "  - key: String, name/key for the generated cluster",
              "  - order: String, in what order to insert the data clusters to the clustering algorithm",
              "    - original",
              "    - reverse",
              "    - spatial",
              "    - reverse-spatial"]
          });
          break;
        default:
          $scope.$broadcast('terminal-output', {
            output: true,
            text: [
              "load [keyword]",
              "  load data into middleware for [keyword] query",
              "cluster [key] [order]",
              "  generate cluster in the middleware for current loaded data",
              "  - key: String, name/key for the generated cluster",
              "  - order: String, in what order to insert the data clusters to the clustering algorithm",
              "    - original",
              "    - reverse",
              "    - spatial",
              "    - reverse-spatial"
            ]
          });
      }
    };

    $scope.validate_cmd = function(tokens) {
      console.log("validating cmd tokens = " + tokens);
      let action = tokens[1];
      switch (action) {
        case "load":
          if (tokens.length === 3) {
            return {
              type: "cmd",
              id: "console",
              keyword: tokens[2],
              cmds: [
                {action: "load", arguments: []}
                ]
            };
          }
          else {
            return {
              type: "cmd",
              id: "console",
              keyword: null,
              cmds: [
                {action: "load", arguments: []}
              ]
            };
          }
        case "cluster":
          if (tokens.length !== 4) {
            $scope.showHelpForCmd(action);
            return null;
          }
          if (tokens[3] !== "original"
            && tokens[3] !== "reverse"
            && tokens[3] !== "spatial"
            && tokens[3] !== "reverse-spatial") {
            $scope.showHelpForCmd(action);
            return null;
          }
          return {
            type: "cmd",
            id: "console",
            keyword: null,
            cmds: [
              {action: "cluster", arguments: [tokens[2], tokens[3]]}
            ]
          };
        default:
          $scope.showHelpForCmd(null);
          return null;
      }
    };

    $scope.showHelpForAnalysis = function(objective) {
      switch (objective) {
        case "distance":
          $scope.$broadcast('terminal-output', {
            output: true,
            text: [
              "distance [key] [zoom] [point1_id] [point2_id]",
              "  calculate distance between point1 and point2 under zoom level in cluster",
              "  - key: String, name/key of the cluster",
              "  - zoom: int",
              "  - point1_id: int",
              "  - point2_id: int"
            ]
          });
          break;
        case "rand-index":
          $scope.$broadcast('terminal-output', {
            output: true,
            text: [
              "rand-index [clusterKey1] [clusterKey2] [zoom]",
              "  calculate rand index of given cluster1 and cluster2 under zoom level",
              "  - clusterKey1: String, name/key of cluster1",
              "  - clusterKey2: String, name/key of cluster2",
              "  - zoom: int"
            ]
          });
          break;
        default:
          $scope.$broadcast('terminal-output', {
            output: true,
            text: [
              "distance [key] [zoom] [point1_id] [point2_id]",
              "  calculate distance between point1 and point2 under zoom level in cluster",
              "  - key: String, name/key of the cluster",
              "  - zoom: int",
              "  - point1_id: int",
              "  - point2_id: int",
              "rand-index [clusterKey1] [clusterKey2] [zoom]",
              "  calculate rand index of given cluster1 and cluster2 under zoom level",
              "  - clusterKey1: String, name/key of cluster1",
              "  - clusterKey2: String, name/key of cluster2",
              "  - zoom: int"
            ]
          });
      }
    };

    $scope.validate_analysis = function(tokens) {
      console.log("validating analysis tokens = " + tokens);
      let objective = tokens[1];
      switch (objective) {
        case "distance":
          if (tokens.length !== 6) {
            $scope.showHelpForAnalysis(objective);
            return null;
          }
          return {
            type: "analysis",
            id: "console",
            keyword: null,
            analysis: {objective: objective, arguments: [tokens[2], tokens[3], tokens[4], tokens[5]]}
          };
        case "rand-index":
          if (tokens.length !== 5) {
            $scope.showHelpForAnalysis(objective);
            return null;
          }
          return {
            type: "analysis",
            id: "console",
            keyword: null,
            analysis: {objective: objective, arguments: [tokens[2], tokens[3], tokens[4]]}
          };
        default:
          $scope.showHelpForAnalysis(null);
          return null;
      }
    };
  });