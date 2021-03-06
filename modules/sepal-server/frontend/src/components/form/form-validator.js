/**
 * @author Mino Togna
 */

var emailRegEx    = /^(([^<>()\[\]\.,;:\s@\"]+(\.[^<>()\[\]\.,;:\s@\"]+)*)|(\".+\"))@(([^<>()[\]\.,;:\s@\"]+\.)+[^<>()[\]\.,;:\s@\"]{2,})$/i
var usernameRegEx = /^[a-zA-Z_][a-zA-Z0-9]{0,29}$/
var passwordRegEx = /^.{6,100}$/

var isValidString = function ( value ) {
    return $.isNotEmptyString( value )
}

var isValidEmail = function ( value ) {
    var valid = false
    if ( isValidString( value ) ) {
        valid = emailRegEx.test( value )
    }
    return valid
}

var isValidUsername = function ( value ) {
    var valid = false
    if ( isValidString( value ) ) {
        valid = usernameRegEx.test( value )
    }
    return valid
}

var isValidPassword = function ( value ) {
    var valid = false
    if ( isValidString( value ) ) {
        valid = passwordRegEx.test( value )
    }
    return valid
}

// ===================
// Utility methods to validate form input fields and shows errors to UI
// ===================
var showError = function ( errorContainer, message ) {
    showMessage( errorContainer, message, 'error' )
}

var showSuccess = function ( container, message ) {
    showMessage( container, message, 'success' )
    var form      = $( container.parentsUntil( 'form' ) )
    var btnSubmit = $( form.find( 'button[type=submit]' ) )
    btnSubmit.disable()
    resetFormErrors( form, container, { delay: 1500, duration: 500 } )
    setTimeout( function () {
        btnSubmit.enable()
    }, 2000 )
}

var showMessage = function ( container, message, type ) {
    container.removeClass( 'form-success form-error' ).addClass( 'form-' + type )
    container.velocitySlideDown( {
        delay: 0, duration: 500, begin: function () {
            container.html( message )
        }
    } )
}

var addError = function ( inputField ) {
    if ( inputField )
        inputField.closest( '.form-group' ).addClass( 'error' )
}

var validateField = function ( field, errorMessage, errorMessageContainer, validation ) {
    if ( !validation( field.val() ) ) {
        addError( field )
        showError( errorMessageContainer, errorMessage )
        return false
    }
    return true
}

var validateString = function ( field, errorMessage, errorMessageContainer ) {
    return validateField( field, errorMessage, errorMessageContainer, isValidString )
}

var validateEmail = function ( field, errorMessage, errorMessageContainer ) {
    return validateField( field, errorMessage, errorMessageContainer, isValidEmail )
}

var validateUsername = function ( field, errorMessage, errorMessageContainer ) {
    return validateField( field, errorMessage, errorMessageContainer, isValidUsername )
}

var validatePassword = function ( field, errorMessage, errorMessageContainer ) {
    return validateField( field, errorMessage, errorMessageContainer, isValidPassword )
}

var resetFormErrors = function ( form, errorMessageContainer, slideOptions ) {
    form.find( '.form-group' ).removeClass( 'error' )
    if ( !errorMessageContainer ) {
        errorMessageContainer = form.find( '.form-notify' )
    }
    if ( errorMessageContainer ) {
        var opts = $.extend( {}, { delay: 0, duration: 0 }, slideOptions )
        errorMessageContainer.velocitySlideUp( opts )
    }
}

var validateForm = function ( form, fields ) {
    var errorContainer = form.find( '.form-notify' )
    
    resetFormErrors( form, errorContainer )
    
    var inputs    = (fields) ? fields : form.find( 'input[type=text], input[type=hidden], input[type=number], input[type=password], textarea' )
    var validForm = true
    $.each( inputs, function () {
        var input    = $( this )
        // var property = input.attr( 'name' )
        var type     = input.data( 'type' )
        var errorMsg = input.data( 'error-message' )
        if ( type ) {
            type     = $.capitalize( type )
            errorMsg = (errorMsg) ? errorMsg : 'Invalid field'
            
            var functx = eval( 'validate' + type )
            if ( functx ) {
                var validInputField = functx.call( null, input, errorMsg, errorContainer )
                validForm           = (validInputField) ? validForm : false
                return validForm
            }
        }
    } )
    
    return validForm
}

module.exports = {
    showError         : showError
    , showSuccess     : showSuccess
    , addError        : addError
    , validateString  : validateString
    , validateEmail   : validateEmail
    , validateUsername: validateUsername
    , validatePassword: validatePassword
    , resetFormErrors : resetFormErrors
    , validateForm    : validateForm
}