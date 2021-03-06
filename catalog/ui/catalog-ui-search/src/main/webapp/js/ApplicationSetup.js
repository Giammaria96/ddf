/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
/*global require, window */
/*jslint nomen:false, -W064 */

// for webpack dev server hot reloading
if (module.hot){
    module.hot.accept(function() {
        // we don't want to refresh the page when using the webpack dev server
    });
}

require('styles/styles.less');
var $ = require('jquery')
$.ajaxSetup({
    cache: false
});

if (process.env.NODE_ENV !== 'production') {
    $('html').addClass('is-development');
    if (module.hot) {
        $('html').addClass('is-hot-reloading');
    }
}

window.CESIUM_BASE_URL = './cesium/';
require([
    'underscore',
    'backbone',
    'marionette',
    'handlebars/dist/handlebars',
    'component/announcement',
    'js/Marionette.Region',
    'js/requestAnimationFramePolyfill',
    'js/HandlebarsHelpers',
    'js/ApplicationHelpers',
    'js/Autocomplete',
    'backbone.customFunctions'
], function(_, Backbone, Marionette, hbs, announcement) {

    let getShortErrorMessage = function (error) {
        var extraMessage = error instanceof Error ? error.name : String(error);

        if (extraMessage.length === 0) {
            return extraMessage;
        }

        // limit to 20 characters so we do not worry about overflow
        if (extraMessage.length > 20) {
            extraMessage = extraMessage.substr(0, 20) + '...';
        }

        return ' - ' + extraMessage;
    };

    let getErrorResponse = function (event, jqxhr, settings, throwError) {
        switch (jqxhr.status) {
            case 403:
                return {title: 'Forbidden', message: 'Not Authorized'};
            case 405:
                return {title: 'Error', message: 'Method not allowed. Please try refreshing your browser'};
            default:
                return {title: 'Error', message: 'Unknown Error' + getShortErrorMessage(throwError)};
        }
    };

    $(window.document).ajaxError(function (event, jqxhr, settings, throwError) {
        if (settings.customErrorHandling) {
            // Do nothing if caller is handling their own error
            return;
        }

        var response = getErrorResponse(event, jqxhr, settings, throwError);
        var message;

        console.error(event, jqxhr, settings, throwError);
        if (jqxhr.getResponseHeader('content-type') === 'application/json' && jqxhr.responseText.startsWith('<') &&
            jqxhr.responseText.indexOf('ACSURL') > -1 && jqxhr.responseText.indexOf('SAMLRequest') > -1) {
            response = {title: 'Logged out', message: 'Please refresh page to log in'}
        } else if (jqxhr.responseJSON !== undefined) {
            message = jqxhr.responseJSON.message;
        }

        announcement.announce({
            title: response.title,
            message: message || response.message,
            type: 'error'
        });

    });

    //in here we drop in any top level patches, etc.
    var toJSON = Backbone.Model.prototype.toJSON;
    Backbone.Model.prototype.toJSON = function(options) {
        var originalJSON = toJSON.call(this, options);
        if (options && options.additionalProperties !== undefined) {
            var backboneModel = this;
            options.additionalProperties.forEach(function(property) {
                originalJSON[property] = backboneModel[property];
            });
        }
        return originalJSON;
    };
    var clone = Backbone.Model.prototype.clone;
    Backbone.Model.prototype.clone = function() {
        var cloneRef = clone.call(this);
        cloneRef._cloneOf = this.id || this.cid;
        return cloneRef;
    };
    var associationsClone = Backbone.AssociatedModel.prototype.clone;
    Backbone.AssociatedModel.prototype.clone = function() {
        var cloneRef = associationsClone.call(this);
        cloneRef._cloneOf = this.id || this.cid;
        return cloneRef;
    };
    Marionette.Renderer.render = function(template, data, view) {
        data._view = view;
        if (typeof template === 'function') {
            return template(data);
        } else {
            return hbs.compile(template)(data);
        }
    };

    // https://github.com/marionettejs/backbone.marionette/issues/3077
    // monkey-patch Marionette for compatibility with jquery 3+.
    // jquery removed the .selector method, which was used by the original
    // implementation here.
    Marionette.Region.prototype.reset = function() {
        this.empty();
        this.el = this.options.el;
        delete this.$el;
        return this;
    };

    require('js/ApplicationStart');
});