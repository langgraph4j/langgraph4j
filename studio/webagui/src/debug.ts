

type DebugConfig = {
    on: boolean,
    topic: string
}


/**
 * 
 * @param {DebugConfig} config 
 */
export const debug = ( config:DebugConfig ) => {
    /**
     * @param { any[] } args 
     */    
    return ( ...args:any[] ) => {
        if( !config.on || args.length === 0 ) return
        if( typeof(args[0]) === 'function' ) {
          args[0]()
          return
        }
        console.debug( `${config.topic}: `, ...args )
      }
}