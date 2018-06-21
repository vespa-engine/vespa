// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* packet-fnetrpc.c */

#ifdef HAVE_CONFIG_H
# include "config.h"
#endif

#include <gmodule.h>
#include <epan/packet.h>
#include <epan/prefs.h>
#include <string.h>

/* forward reference */
void proto_register_fnetrpc();
void proto_reg_handoff_fnetrpc();
static void dissect_fnetrpc(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree);

static void decode_params_bigend(proto_tree *tree, tvbuff_t *tvb, int offset, int len);
static void decode_params_litend(proto_tree *tree, tvbuff_t *tvb, int offset, int len);

/* Define version if we are not building ethereal statically */
#ifndef ENABLE_STATIC
G_MODULE_EXPORT const gchar version[] = "0.0";
#endif

static int proto_fnetrpc = -1;
static int global_fnetrpc_port = 10101;
static dissector_handle_t fnetrpc_handle;

#ifndef ENABLE_STATIC
G_MODULE_EXPORT void
plugin_register(void)
{
        /* register the new protocol, protocol fields, and subtrees */
        if (proto_fnetrpc == -1) { /* execute protocol initialization only once */
                proto_register_fnetrpc();
        }
}

G_MODULE_EXPORT void
plugin_reg_handoff(void){
        proto_reg_handoff_fnetrpc();
}
#endif

#define FRT_LITTLE_ENDIAN_FLAG	0x01
#define FRT_NOREPLY_FLAG	0x02

static int hf_fnetrpc_packet_type  = -1;
static int hf_fnetrpc_packet_flags = -1;
static int hf_fnetrpc_packet_len   = -1;
static int hf_fnetrpc_packet_reqid = -1;
static int hf_fnetrpc_req_method   = -1;

static int hf_fnetrpc_typestring   = -1;
static int hf_fnetrpc_val_int32    = -1;
static int hf_fnetrpc_val_array    = -1;
static int hf_fnetrpc_val_string   = -1;

static int hf_fnetrpc_noreply_flag = -1;
static int hf_fnetrpc_litend_flag  = -1;

static gint ett_fnetrpc = -1;
static gint ett_fnetrpc_params = -1;
static gint ett_fnetrpc_retval = -1;

static const value_string packettypenames[] = {
	{ 100, "RPC Request" },
	{ 101, "RPC Reply" },
	{ 102, "RPC Error" },
	{ 0,   NULL },
};

static hf_register_info hf[] = {
    { &hf_fnetrpc_packet_len,
	{ "FRT Packet length", "fnetrpc.packetlen",
	FT_UINT32, BASE_DEC, NULL, 0x0,
	"", HFILL }
    },
    { &hf_fnetrpc_packet_flags,
	{ "FRT Packet flags", "fnetrpc.packetflags",
	FT_UINT16, BASE_HEX, NULL, 0x0,
	"", HFILL }
    },
    { &hf_fnetrpc_packet_type,
	{ "FRT Packet type", "fnetrpc.packettype",
	FT_UINT16, BASE_HEX, VALS(packettypenames), 0x0,
	"", HFILL }
    },
    { &hf_fnetrpc_noreply_flag,
	{ "FRT noreply flag", "fnetrpc.flags.noreply",
	FT_BOOLEAN, 8, NULL, FRT_NOREPLY_FLAG,
	"", HFILL }
    },
    { &hf_fnetrpc_litend_flag,
	{ "FRT little-endian flag", "fnetrpc.flags.littleendian",
	FT_BOOLEAN, 8, NULL, FRT_LITTLE_ENDIAN_FLAG,
	"", HFILL }
    },
    { &hf_fnetrpc_packet_reqid,
	{ "FRT Request id", "fnetrpc.requestid",
	FT_UINT32, BASE_DEC, NULL, 0x0,
	"", HFILL }
    },
    { &hf_fnetrpc_req_method,
      { "FRT name of called method", "fnetrpc.request.method",
        FT_STRING, 0, NULL, 0x0,
	"", HFILL }
    },

    { &hf_fnetrpc_typestring,
      { "FRT value typestring", "fnetrpc.value.typestring",
        FT_STRING, 0, NULL, 0x0,
	"", HFILL }
    },
    { &hf_fnetrpc_val_int32,
	{ "FRT int32 value", "fnetrpc.value.int32",
	FT_UINT32, BASE_DEC, NULL, 0x0,
	"", HFILL }
    },
    { &hf_fnetrpc_val_array,
	{ "FRT array length", "fnetrpc.value.array",
	FT_UINT32, BASE_DEC, NULL, 0x0,
	"", HFILL }
    },
    { &hf_fnetrpc_val_string,
      { "FRT string value", "fnetrpc.value.string",
        FT_STRING, 0, NULL, 0x0,
	"", HFILL }
    },

};

/* Setup protocol subtree array */
static gint *ett[] = {
	&ett_fnetrpc,
	&ett_fnetrpc_params,
	&ett_fnetrpc_retval,
};


void
proto_register_fnetrpc(void)
{
    module_t *fnetrpc_module;

    if (proto_fnetrpc == -1) {
	proto_fnetrpc = proto_register_protocol (
		"FNET Remote Tools Protocol",   /* name */
		"FRT",                          /* short name */
		"frt"                           /* abbrev */
	);
    }
    fnetrpc_module  = prefs_register_protocol(proto_fnetrpc, proto_reg_handoff_fnetrpc);

    proto_register_field_array(proto_fnetrpc, hf, array_length(hf));
    proto_register_subtree_array(ett, array_length(ett));
}


void
proto_reg_handoff_fnetrpc(void)
{
    static int Initialized=FALSE;

    if (!Initialized) {
	fnetrpc_handle = create_dissector_handle(dissect_fnetrpc, proto_fnetrpc);
	dissector_add("tcp.port", global_fnetrpc_port, fnetrpc_handle);
    }
}

static void
decode_rpc(int packet_type, proto_tree *tree, tvbuff_t *tvb, int offset, int iplen)
{
    int islitend = 0;
    proto_tree *my_tree = NULL;
    proto_item *ti = proto_tree_add_item(tree, proto_fnetrpc, tvb, offset, iplen, FALSE);

    proto_item_append_text(ti, ", %s", val_to_str(packet_type, packettypenames, "Unknown type (%d)"));

    my_tree = proto_item_add_subtree(ti, ett_fnetrpc);

    proto_tree_add_item(my_tree, hf_fnetrpc_packet_flags, tvb, offset,   2, FALSE);
    proto_tree_add_item(my_tree, hf_fnetrpc_noreply_flag, tvb, offset+1, 1, FALSE);
    proto_tree_add_item(my_tree, hf_fnetrpc_litend_flag,  tvb, offset+1, 1, FALSE);
    proto_tree_add_item(my_tree, hf_fnetrpc_packet_type,  tvb, offset+2, 2, FALSE);
    proto_tree_add_item(my_tree, hf_fnetrpc_packet_reqid, tvb, offset+4, 4, FALSE);

    islitend = (tvb_get_guint8(tvb, offset+1) & FRT_LITTLE_ENDIAN_FLAG);

    iplen -= 8; // consumed as headers
    offset += 8;
    
    // fprintf(stderr, "type %d / iplen %d\n", packet_type, iplen);
    if (packet_type == 101) {
        proto_tree *retval_tree = NULL;
        retval_tree = proto_item_add_subtree(my_tree, ett_fnetrpc_retval);
        if (islitend) {
            decode_params_litend(retval_tree, tvb, offset, iplen);
        } else {
            decode_params_bigend(retval_tree, tvb, offset, iplen);
        }
    }

    if (packet_type == 100) {
        gint slen = 0;
        int ssz = 0;
        guint8 buf[256];

        // 4 bytes integer for length of method name
        if (iplen < 4) return;

        if (islitend) {
            ssz = tvb_get_letohl(tvb, offset);
        } else {
            ssz = tvb_get_ntohl(tvb, offset);
        }
        offset += 4;
        iplen -= 4;
        
        // fprintf(stderr, "ssz %d\n", ssz);
        
        // ssz bytes of method name
        if (iplen >= ssz) {
            proto_tree *param_tree = NULL;
            
            // max 255 bytes displayed
            slen = tvb_get_nstringz0(tvb, offset, 1 + ( ssz > 255 ? 255 : ssz), buf);
            proto_tree_add_string(my_tree, hf_fnetrpc_req_method, tvb, offset, ssz, buf);
            offset += ssz;
            iplen -= ssz;
            // fprintf(stderr, "slen %d\n", slen);
            
            proto_item_append_text(ti, ": %s()", buf);
            
            param_tree = proto_item_add_subtree(my_tree, ett_fnetrpc_params);
            if (islitend) {
                decode_params_litend(param_tree, tvb, offset, iplen);
            } else {
                decode_params_bigend(param_tree, tvb, offset, iplen);
            }
        }
    }
}

static void
dissect_fnetrpc(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree)
{
    int plen = tvb_length(tvb);
    guint16 packet_type = 0;

    /* if displaying protocol name, set to our name */
    if (check_col(pinfo->cinfo, COL_PROTOCOL)) {
	    col_set_str(pinfo->cinfo, COL_PROTOCOL, "FRT");
    }
    /* Clear out stuff in the info column */
    if (check_col(pinfo->cinfo, COL_INFO)) {
	    col_clear(pinfo->cinfo,COL_INFO);
    }

    if (plen < 12) {
	if (check_col(pinfo->cinfo, COL_INFO)) {
	    col_add_str(pinfo->cinfo, COL_INFO, "Too short packet");
	}
	return;
    }

    packet_type = tvb_get_ntohs(tvb, 6);

    /* Clear out stuff in the info column */
    if (check_col(pinfo->cinfo, COL_INFO)) {
	    col_clear(pinfo->cinfo,COL_INFO);
	    col_add_fstr(pinfo->cinfo, COL_INFO, "%s",
		   val_to_str(packet_type, packettypenames,
			      "Unknown type (%d)"));
    }

    if (tree) { /* being asked for details */
        int offset = 0;
        
        while (offset + 4 < plen) {
            int iplen = tvb_get_ntohl(tvb, offset);
            proto_item *itlen =
                proto_tree_add_item(tree, hf_fnetrpc_packet_len, tvb, offset, 4, FALSE);

            offset += 4;
            if (offset + iplen > plen) {
                proto_item_append_text(itlen, " (%d bytes missing)", offset + iplen - plen);
                iplen = plen - offset;
            }
            if (iplen < 8)
                break;

            decode_rpc(packet_type, tree, tvb, offset, iplen);
            // if (offset + iplen < plen) {
            // fprintf(stderr, "decoded %d bytes at %d, of total %d\n", iplen, offset, plen);;
            // }
            offset += iplen;

        }
        if (plen > offset) {
            proto_item *extra = proto_tree_add_item(tree, hf_fnetrpc_packet_len, tvb, offset, plen - offset, FALSE);
            proto_item_append_text(extra, " (%d undecoded bytes)", plen - offset);
        }
    }
}

void
decode_params_litend(proto_tree *tree, tvbuff_t *tvb, int offset, int len)
{
    proto_item *tsit = NULL;
    int i;
    int ssz;
    guint8 typestring[256];
    guint8 buf[256];
    gint slen = 0;

    if (len < 4) return;
    
    ssz = tvb_get_letohl(tvb, offset);

    offset += 4;
    len -= 4;
    if (ssz > len) return;
    
    slen = tvb_get_nstringz0(tvb, offset, 1 + ( ssz > 255 ? 255 : ssz), typestring);
    tsit= proto_tree_add_string(tree, hf_fnetrpc_typestring, tvb, offset, ssz, typestring);
    
    offset += ssz;
    len -= ssz;

    for (i = 0; i < (int)strlen(typestring); i++) {
        int j = 0;
        int narr = 0;

        switch (typestring[i]) {

        case 'i':
            if (len < 4) return;
            proto_tree_add_item(tree, hf_fnetrpc_val_int32, tvb, offset, 4, TRUE);
            offset += 4;
            len -= 4;
            break;

        case 's':
            if (len < 4) return;
            ssz = tvb_get_letohl(tvb, offset);
            offset += 4;
            len -= 4;
            if (ssz > len) return;
            slen = tvb_get_nstringz0(tvb, offset, 1 + ( ssz > 255 ? 255 : ssz), buf);
            proto_tree_add_string(tree, hf_fnetrpc_val_string, tvb, offset, ssz, buf);
            offset += ssz;
            len -= ssz;
            break;
            
        case 'S':
            if (len < 4) return;
            narr = tvb_get_letohl(tvb, offset);
            proto_tree_add_item(tree, hf_fnetrpc_val_array, tvb, offset, 4, TRUE);
            offset += 4;
            len -= 4;
            for (j = 0; j < narr; j++) {
                if (len < 4) return;
                ssz = tvb_get_letohl(tvb, offset);
                offset += 4;
                len -= 4;
                if (ssz > len) return;
                slen = tvb_get_nstringz0(tvb, offset, 1 + ( ssz > 255 ? 255 : ssz), buf);
                proto_tree_add_string(tree, hf_fnetrpc_val_string, tvb, offset, ssz, buf);
                offset += ssz;
                len -= ssz;
            }
            break;

        default:
            proto_item_append_text(tsit, " unknown value type '%c' (0x%02x)", 
                                   (int)typestring[i], (int)typestring[i]);
        }
    }
}



void
decode_params_bigend(proto_tree *tree, tvbuff_t *tvb, int offset, int len)
{
    LOG_ABORT("should not be reached");
}
