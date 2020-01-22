module.exports = {
    client: {
      service: {
        name: 'StarWars',
        url: 'https://api.graph.cool/simple/v1/swapi'
      },
      includes: [ "src/main/graphql/*.graphql" ]
    }
  };