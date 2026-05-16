

/**
 * Configuration object for debugging settings.
 *
 * @typedef {object} DebugConfig
 * @property {boolean} on - Flag indicating whether debugging is enabled.
 * @property {string} topic - The specific topic or category to filter debug messages for.
 */


/**
 * 
 * @param {DebugConfig} config 
 */
export const debug = ( config ) => {
    /**
     * @param { any[] } args 
     */    
    return ( ...args ) => {
        if( !config.on || args.length === 0 ) return
        if( typeof(args[0]) === 'function' ) {
          args[0]()
          return
        }
        console.debug( `${config.topic}: `, ...args )
      }
}